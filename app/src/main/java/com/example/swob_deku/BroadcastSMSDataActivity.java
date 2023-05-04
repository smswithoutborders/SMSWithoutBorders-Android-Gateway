package com.example.swob_deku;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Base64;
import android.util.Log;

import com.example.swob_deku.Commons.Helpers;
import com.example.swob_deku.Models.Images.ImageHandler;
import com.example.swob_deku.Models.SMS.SMSHandler;
import com.example.swob_deku.Models.Security.SecurityDH;
import com.example.swob_deku.Models.Security.SecurityHelpers;
import com.google.i18n.phonenumbers.NumberParseException;

//import org.bouncycastle.operator.OperatorCreationException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class BroadcastSMSDataActivity extends BroadcastReceiver {

    public static String DATA_BROADCAST_INTENT = BuildConfig.APPLICATION_ID + ".DATA_SMS_RECEIVED_ACTION" ;

    @Override
    public void onReceive(Context context, Intent intent) {
        /**
         * Important note: either image or dump it
         */

        if(BuildConfig.DEBUG)
            Log.d(getClass().getName(), "New data received..");

        if (intent.getAction().equals(Telephony.Sms.Intents.DATA_SMS_RECEIVED_ACTION)) {
            if (getResultCode() == Activity.RESULT_OK) {
                ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream();
                String address = "";

                for (SmsMessage currentSMS : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                    address = currentSMS.getDisplayOriginatingAddress();

                    try {
                        messageBuffer.write(currentSMS.getUserData());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                long messageId = -1;
                try {
                    address = Helpers.formatPhoneNumbers(address);
                } catch (NumberParseException e) {
                    throw new RuntimeException(e);
                }
                try {
                    String strMessage = messageBuffer.toString();
                    Log.d(getClass().getName(), "Data received broadcast: " + strMessage);
                    if(strMessage.contains(SecurityHelpers.FIRST_HEADER) &&
                            strMessage.contains(SecurityHelpers.END_HEADER)) {
                        strMessage = registerIncomingAgreement(context, address,
                                messageBuffer.toByteArray(), -1);
                    }
                    else if (strMessage.contains(SecurityHelpers.FIRST_HEADER)) {
                        // TODO: register message and store the reference in a shared reference location
//                        messageId = SMSHandler.registerIncomingMessage(context, address, strMessage);
                        strMessage = registerIncomingAgreement(context, address, messageBuffer.toByteArray(), 0);
                    }
                    else if (strMessage.contains(SecurityHelpers.END_HEADER)) {
                        // TODO: search for registered message and get content from shared reference location
//                        messageId = SMSHandler.registerIncomingMessage(context, address, strMessage);
                        strMessage = registerIncomingAgreement(context, address, messageBuffer.toByteArray(), 1);
                    }

                    if(checkMessagesAvailable(context, address)) {
                        String notificationNote = "New Key request";

                        strMessage = SecurityHelpers.FIRST_HEADER +
                                strMessage + SecurityHelpers.END_HEADER;

                        messageId = SMSHandler.registerIncomingMessage(context, address, strMessage);
                        BroadcastSMSTextActivity.sendNotification(context, notificationNote, address, messageId);
                    }
                    broadcastIntent(context);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String registerIncomingAgreement(Context context, String msisdn, byte[] keyPart, int part) throws GeneralSecurityException, IOException {
        SecurityDH securityDH = new SecurityDH(context);
        return securityDH.securelyStorePublicKeyKeyPair(context, msisdn, keyPart, part);
    }


    private void broadcastIntent(Context context) {
        Intent intent = new Intent(DATA_BROADCAST_INTENT);
        context.sendBroadcast(intent);
    }

    private boolean checkMessagesAvailable(Context context, String msisdn) throws GeneralSecurityException, IOException {
        SecurityDH securityDH = new SecurityDH(context);
        return securityDH.peerAgreementPublicKeysAvailable(context, msisdn);
    }
}
