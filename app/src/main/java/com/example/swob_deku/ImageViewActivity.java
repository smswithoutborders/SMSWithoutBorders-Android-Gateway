package com.example.swob_deku;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Base64;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.example.swob_deku.Commons.Helpers;
import com.example.swob_deku.Models.Images.ImageHandler;
import com.example.swob_deku.Models.SIMHandler;
import com.example.swob_deku.Models.SMS.SMS;
import com.example.swob_deku.Models.SMS.SMSHandler;

import java.io.IOException;
import java.util.ArrayList;

public class ImageViewActivity extends AppCompatActivity {

    Uri imageUri;
    ImageView imageView;

    TextView imageDescription;

    Bitmap compressedBitmap;
    byte[] compressedBytes;

    String address = "";
    String threadId = "";

    ImageHandler imageHandler;

    final int MAX_RESOLUTION = 768;
    final int MIN_RESOLUTION = MAX_RESOLUTION / 2;
    int COMPRESSION_RATIO = 0;

    public double changedResolution;

    public static final String IMAGE_INTENT_EXTRA = "image_sms_id";

    public static final String SMS_IMAGE_PENDING_LOCATION = "SMS_IMAGE_PENDING_LOCATION";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_view);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.image_view_toolbar);
//        myToolbar.inflateMenu(R.menu.default_menu);
        setSupportActionBar(myToolbar);

        // Get a support ActionBar corresponding to this toolbar
        ActionBar ab = getSupportActionBar();

        // Enable the Up button
        ab.setDisplayHomeAsUpEnabled(true);

        imageView = findViewById(R.id.compressed_image_holder);
        imageDescription = findViewById(R.id.image_details_size);

        if(getIntent().hasExtra(IMAGE_INTENT_EXTRA)) {
            String smsId = getIntent().getStringExtra(IMAGE_INTENT_EXTRA);

            // TODO: Get all messages which have the Ref ID
            // TODO: get until the len of messages have been acquired, then fit them together
            // TODO: until the len has been acquired.

            Cursor cursor = SMSHandler.fetchSMSInboxById(getApplicationContext(), smsId);
            if(cursor.moveToFirst()) {
                SMS sms = new SMS(cursor);

                byte[] body = Base64.decode(sms.getBody()
                        .replace(ImageHandler.IMAGE_HEADER, ""), Base64.DEFAULT);
                try {
                    buildImage(body);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            cursor.close();
        }
        else {
            address = getIntent().getStringExtra(SMSSendActivity.ADDRESS);
            threadId = getIntent().getStringExtra(SMSSendActivity.THREAD_ID);
            imageUri = Uri.parse(getIntent().getStringExtra(SMSSendActivity.IMAGE_URI));

            try {
                imageHandler = new ImageHandler(getApplicationContext(), imageUri);

                ((TextView)findViewById(R.id.image_details_original_resolution))
                        .setText("Original resolution: "
                                + imageHandler.bitmap.getWidth()
                                + " x "
                                + imageHandler.bitmap.getHeight());

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try {
//                changedResolution = getMaxResolution();
                changedResolution = MAX_RESOLUTION;
                buildImage();
                changeResolution(getMaxResolution());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home ) {
            Intent intent = new Intent(this, SMSSendActivity.class);
            intent.putExtra(SMSSendActivity.ADDRESS, address);

            if(!threadId.isEmpty())
                intent.putExtra(SMSSendActivity.THREAD_ID, threadId);

            startActivity(intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void changeResolution(final int maxResolution) {
        final double resDifference = maxResolution - MAX_RESOLUTION;
        final double changeConstant = resDifference / 100;

        SeekBar seekBar = findViewById(R.id.image_view_change_resolution_seeker);

        TextView seekBarProgress = findViewById(R.id.image_details_seeker_progress);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            final int resChangeRatio = Math.round(MIN_RESOLUTION / seekBar.getMax());
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // TODO: change the resolution text
                double calculatedResolution = progress == 0 ? MAX_RESOLUTION :
                        MAX_RESOLUTION - (resChangeRatio * progress);
//
//                if(calculatedResolution > MIN_RESOLUTION) {
//                    changedResolution = calculatedResolution;
//                    COMPRESSION_RATIO = 0;
//                } else {
//                    changedResolution = MIN_RESOLUTION;
//                    COMPRESSION_RATIO = seekBar.getMax() - progress;
//                }
                changedResolution = calculatedResolution;
                COMPRESSION_RATIO = progress;
                seekBarProgress.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO: put loader
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO: compress the image
                try {
                    buildImage();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private void buildImage(byte[] data ) throws IOException {
        compressedBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        imageView.setImageBitmap(compressedBitmap);
    }

    private int getMaxResolution() {
        return imageHandler.getMaxResolution();
    }

    private void buildImage() throws IOException {
        SmsManager smsManager = Build.VERSION.SDK_INT > Build.VERSION_CODES.R ?
                getSystemService(SmsManager.class) : SmsManager.getDefault();

//        compressedBytes = imageHandler.compressImage(COMPRESSION_RATIO, imageHandler.bitmap);
//        compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.length);
//        imageHandler.bitmap = compressedBitmap;
//        Bitmap imageBitmap = imageHandler.resizeImage(changedResolution);
//        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//        imageBitmap.compress(Bitmap.CompressFormat.WEBP, 100, byteArrayOutputStream);
//        compressedBytes = byteArrayOutputStream.toByteArray();

        Bitmap imageBitmap = imageHandler.resizeImage(changedResolution);
        compressedBytes = imageHandler.compressImage(COMPRESSION_RATIO, imageBitmap);

        ArrayList<String> dividedArray = smsManager.divideMessage(
                Base64.encodeToString(compressedBytes, Base64.DEFAULT));

//        byte[] riffHeader = SMSHandler.copyBytes(compressedBytes, 0, 12);
//        byte[] vp8Header = SMSHandler.copyBytes(compressedBytes, 12, 4);

        TextView imageResolution = findViewById(R.id.image_details_resolution);
        imageResolution.setText("New resolution: " + imageBitmap.getWidth() + " x " + imageBitmap.getHeight());

        TextView imageSize = findViewById(R.id.image_details_size);
        imageSize.setText("Size " + (compressedBytes.length / 1024) + " KB");

        TextView imageQuality = findViewById(R.id.image_details_quality);
        imageQuality.setText("Quality " + COMPRESSION_RATIO + "%");

        TextView imageSMSCount = findViewById(R.id.image_details_sms_count);
        imageSMSCount.setText(dividedArray.size() + " Messages");

        compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.length);
        imageView.setImageBitmap(compressedBitmap);
    }

    public void sendImage(View view) throws InterruptedException {
        Intent intent = new Intent(this, SMSSendActivity.class);
        intent.putExtra(SMSSendActivity.ADDRESS, address);

        long messageId = Helpers.generateRandomNumber();

        int subscriptionId = SIMHandler.getDefaultSimSubscription(getApplicationContext());

        String threadIdRx = SMSHandler.registerPendingMessage(getApplicationContext(),
                address,
                ImageHandler.IMAGE_HEADER + Base64.encodeToString(compressedBytes, Base64.DEFAULT),
                messageId,
                subscriptionId);

        intent.putExtra(SMSSendActivity.THREAD_ID, threadIdRx);
        intent.putExtra(SMS_IMAGE_PENDING_LOCATION, messageId);

        startActivity(intent);
        finish();
    }


    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, SMSSendActivity.class);
        intent.putExtra(SMSSendActivity.ADDRESS, address);

        if(!threadId.isEmpty())
            intent.putExtra(SMSSendActivity.THREAD_ID, threadId);

        startActivity(intent);
        finish();
    }
}