package com.tomclaw.mandarin.im.icq;

import com.tomclaw.mandarin.im.AccountRoot;
import com.tomclaw.mandarin.im.Request;

/**
 * Created with IntelliJ IDEA.
 * User: solkin
 * Date: 6/12/13
 * Time: 1:38 PM
 */
public class IcqMessageRequest extends Request {

    @Override
    public int onRequest(AccountRoot accountRoot) {
        return REQUEST_SENT;
    }

    @Override
    public void onResponse() {

    }
}
