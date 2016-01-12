package cm.aptoide.pt.openiab.webservices;


import java.util.HashMap;

import cm.aptoide.pt.openiab.webservices.json.IabPurchaseStatusJson;

public class PaidAppPurchaseStatusRequest extends BasePurchaseStatusRequest {

    @Override
    IabPurchaseStatusJson executeRequest(Webservice webervice, HashMap<String, String> parameters) {
        return webervice.checkProductPayment(parameters);
    }

    String getReqType(){
        return "apkpurchasestatus";
    }

}
