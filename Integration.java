public class OrderQueueableSAPIntegration implements Queueable, Database.AllowsCallouts {
	private Id orderIdToSend;
    
    public OrderQueueableSAPIntegration(Id orderId){
        orderIdToSend = orderId;
        System.debug('OrderQueueableSAPIntegration.constructor.orderIdToSend....: ' + orderIdToSend);
    }
    
    public void execute(QueueableContext context) {
        System.debug('OrderQueueableSAPIntegration.execute - INI');
        System.debug('OrderQueueableSAPIntegration.execute.orderId....: ' + orderIdToSend);
        
        Order orderWithFields;
        List<Order> o = OrderDataQuery.findById(orderIdToSend);
        if (o != null) {
            if (o.size() > 0) {
                orderWithFields = o[0];
            }
        }

        if (orderWithFields == null) {
            return;
        }
        
        System.debug('OrderQueueableSAPIntegration.execute - orderWithFields: ' + orderWithFields);

        OrderSAPIntegrationSerializer serializer = new OrderSAPIntegrationSerializer();
        OrderSAPIntegrationSoapXml integrationService = new OrderSAPIntegrationSoapXml();
		
		String xmlBody      = null;
        String errorMessage = null;
        Boolean hasErrors   = false;
        String statusMotivo = 'FalhaEnvioSAP';
        
        try {
            xmlBody = serializer.serialize(orderWithFields);
        } catch (Exception ex) {
            System.debug('OrderQueueableSAPIntegration.execute - serializer Exception: ' + ex);
            hasErrors       = true;
            errorMessage    = ex.getMessage();
            IntegrationUtils.WSLog('Order OUT', 'POST', errorMessage,   true,   orderIdToSend);
        }

        if (!hasErrors) {
            try {
                integrationService.send(orderWithFields, xmlBody);
            } catch (Exception ex) {
                System.debug('OrderQueueableSAPIntegration.execute - integrationService Exception: ' + ex);
                hasErrors       = true;
                errorMessage    = ex.getMessage();
                IntegrationUtils.WSLog('Order OUT', 'POST', xmlBody,        false,  orderIdToSend);
                IntegrationUtils.WSLog('Order OUT', 'OUT',  errorMessage,   true,   orderIdToSend);

                if (errorMessage.toUpperCase().contains('READ TIMED OUT')){
                    statusMotivo    = 'TimedOut';
                }
            }
        }

        if (hasErrors) {
            System.debug('OrderQueueableSAPIntegration.execute - hasErrors');
            Order orderToUpdate = new Order(Id = orderIdToSend);
        	orderToUpdate.Status 				= 'Integracao';
			orderToUpdate.StatusMotivo__c		= statusMotivo;
        	orderToUpdate.IntegrationMessage__c	= errorMessage;
            //Campo para não cair na validação de permissionamento da classe PermissionManager
		    orderToUpdate.ByPassPermission__c	= true;
        	update orderToUpdate;
        }
        
        System.debug('OrderQueueableSAPIntegration.execute - END');
    }

}
