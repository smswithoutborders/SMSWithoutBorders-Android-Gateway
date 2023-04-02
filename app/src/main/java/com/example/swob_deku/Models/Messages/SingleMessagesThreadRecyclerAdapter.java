package com.example.swob_deku.Models.Messages;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.provider.Telephony;
import android.text.format.DateUtils;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.paging.ItemSnapshotList;
import androidx.paging.PagingData;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.swob_deku.ImageViewActivity;
import com.example.swob_deku.Models.Images.ImageHandler;
import com.example.swob_deku.Models.SMS.SMS;
import com.example.swob_deku.Models.SMS.SMSHandler;
import com.example.swob_deku.R;
import com.example.swob_deku.SMSSendActivity;
import com.google.android.material.card.MaterialCardView;

import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//public class SingleMessagesThreadRecyclerAdapter extends PagingDataAdapter<SMS, RecyclerView.ViewHolder> {
public class SingleMessagesThreadRecyclerAdapter extends RecyclerView.Adapter {
    Context context;
    Toolbar toolbar;
    String highlightedText;
    View highlightedView;

//    private int selectedItem = RecyclerView.NO_POSITION;
    public LiveData<List<String>> selectedItem = new MutableLiveData<>();
    MutableLiveData<List<String>> mutableSelectedItems = new MutableLiveData<>();

    public final AsyncListDiffer<SMS> mDiffer = new AsyncListDiffer(this, SMS.DIFF_CALLBACK);


    final int MESSAGE_TYPE_ALL = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_ALL;
    final int MESSAGE_TYPE_INBOX = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX;

    final int TIMESTAMP_MESSAGE_TYPE_INBOX = 600;
    final int TIMESTAMP_MESSAGE_TYPE_OUTBOX = 700;

    final int MESSAGE_TYPE_SENT = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT;
    final int MESSAGE_TYPE_DRAFT = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT;
    final int MESSAGE_TYPE_OUTBOX = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX;
    final int MESSAGE_TYPE_FAILED = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED;
    final int MESSAGE_TYPE_QUEUED = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_QUEUED;

    public SingleMessagesThreadRecyclerAdapter(Context context) {
//        super(SMS.DIFF_CALLBACK);
        this.context = context;
        this.selectedItem = mutableSelectedItems;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // https://developer.android.com/reference/android/provider/Telephony.TextBasedSmsColumns#MESSAGE_TYPE_OUTBOX
        LayoutInflater inflater = LayoutInflater.from(this.context);

        if( viewType == TIMESTAMP_MESSAGE_TYPE_INBOX ) {
            View view = inflater.inflate(R.layout.messages_thread_received_layout, parent, false);
            return new TimestampMessageReceivedViewHandler(view);
        }
        else if( viewType == MESSAGE_TYPE_INBOX ) {
            View view = inflater.inflate(R.layout.messages_thread_received_layout, parent, false);
            return new MessageReceivedViewHandler(view);
        }
        else if( viewType == TIMESTAMP_MESSAGE_TYPE_OUTBOX ) {
            View view = inflater.inflate(R.layout.messages_thread_sent_layout, parent, false);
            return new TimestampMessageSentViewHandler(view);
        }

        View view = inflater.inflate(R.layout.messages_thread_sent_layout, parent, false);
        return new MessageSentViewHandler(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
//        final SMS sms = (SMS) snapshot().get(position);
        int finalPosition = position;

        final SMS sms = (SMS) mDiffer.getCurrentList().get(position);
        final String smsId = sms.getId();

        String date = sms.getDate();
        if (DateUtils.isToday(Long.parseLong(date))) {
            DateFormat dateFormat = new SimpleDateFormat("h:mm a");
            date = "Today " + dateFormat.format(new Date(Long.parseLong(date)));
        }
        else {
            DateFormat dateFormat = new SimpleDateFormat("EE h:mm a");
            date = dateFormat.format(new Date(Long.parseLong(date)));
        }

        if(holder instanceof MessageReceivedViewHandler) {
            MessageReceivedViewHandler messageReceivedViewHandler = (MessageReceivedViewHandler) holder;
            if(holder instanceof TimestampMessageReceivedViewHandler)
                messageReceivedViewHandler.timestamp.setText(date);
            else
                messageReceivedViewHandler.timestamp.setVisibility(View.GONE);

            TextView receivedMessage = messageReceivedViewHandler.receivedMessage;
            receivedMessage.setText(sms.getBody());

            TextView dateView = messageReceivedViewHandler.date;
            dateView.setVisibility(View.INVISIBLE);
            dateView.setText(date);


            messageReceivedViewHandler.constraintLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(sms.getBody().contains(ImageHandler.IMAGE_HEADER)) {
                        Intent intent = new Intent(context, ImageViewActivity.class);
                        intent.putExtra(ImageViewActivity.IMAGE_INTENT_EXTRA, sms.getId());
                        intent.putExtra(SMSSendActivity.THREAD_ID, sms.getThreadId());
                        intent.putExtra(SMSSendActivity.ID, sms.getId());

                        context.startActivity(intent);
                    }
                }
            });

            messageReceivedViewHandler.constraintLayout.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if(selectedItem.getValue() == null) {
                        Log.d(getClass().getName(), "should be updating items..");
                        List<String> newItems = new ArrayList<>();
                        newItems.add(smsId);
                        mutableSelectedItems.setValue(newItems);

                        messageReceivedViewHandler.highlight();
                        return true;
                    }
                    else if(!selectedItem.getValue().contains(finalPosition)) {
                        List<String> previousItems = selectedItem.getValue();
                        previousItems.add(smsId);
                        mutableSelectedItems.setValue(previousItems);
                        return true;
                    }
                    return false;
                }
            });

        }
        else {
            MessageSentViewHandler messageSentViewHandler = (MessageSentViewHandler) holder;
            messageSentViewHandler.sentMessage.setText(sms.getBody());
            messageSentViewHandler.date.setText(date);

            if(holder instanceof TimestampMessageSentViewHandler)
                messageSentViewHandler.timestamp.setText(date);
            else
                messageSentViewHandler.timestamp.setVisibility(View.GONE);

            final int status = sms.getStatusCode();
            String statusMessage = status == Telephony.TextBasedSmsColumns.STATUS_COMPLETE ?
                    "delivered" : "sent";

            statusMessage = status == Telephony.TextBasedSmsColumns.STATUS_PENDING ?
                    "sending..." : statusMessage;

            statusMessage = status == Telephony.TextBasedSmsColumns.STATUS_FAILED ?
                    "failed!" : statusMessage;

            statusMessage = "• " + statusMessage;

            messageSentViewHandler.sentMessageStatus.setText(statusMessage);
            messageSentViewHandler.constraintLayout.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return true;
                }
            });
        }
    }

    @Override
    public int getItemViewType(int position) {
//        ItemSnapshotList snapshotList = this.snapshot();
        List snapshotList = mDiffer.getCurrentList();
        SMS sms = (SMS) snapshotList.get(position);
        if (position != 0 && (position == snapshotList.size() - 1 ||
                !SMSHandler.isSameHour(sms,
                        (SMS) snapshotList.get(position + 1)))) {
            return (sms.getType() == MESSAGE_TYPE_INBOX) ?
                    TIMESTAMP_MESSAGE_TYPE_INBOX : TIMESTAMP_MESSAGE_TYPE_OUTBOX;
        } else {
//            int messageType = mDiffer.getCurrentList().get(position).getType();
//            return (messageType > -1 )? messageType : 0;
            return sms.getType();
        }
    }

    @Override
    public int getItemCount() {
        return mDiffer.getCurrentList().size();
    }

    public static class MessageSentViewHandler extends RecyclerView.ViewHolder {
         TextView sentMessage;
         TextView sentMessageStatus;
         TextView date;
         TextView timestamp;

         ConstraintLayout constraintLayout;
        public MessageSentViewHandler(@NonNull View itemView) {
            super(itemView);
            sentMessage = itemView.findViewById(R.id.message_sent_text);
            sentMessageStatus = itemView.findViewById(R.id.message_thread_sent_status_text);
            date = itemView.findViewById(R.id.message_thread_sent_date_text);
            timestamp = itemView.findViewById(R.id.sent_message_date_segment);
            constraintLayout = itemView.findViewById(R.id.message_sent_constraint);
        }
    }

    public static class TimestampMessageSentViewHandler extends MessageSentViewHandler {
        public TimestampMessageSentViewHandler(@NonNull View itemView) {
            super(itemView);
        }
    }

    public static class MessageReceivedViewHandler extends RecyclerView.ViewHolder {
        TextView receivedMessage;
        TextView date;
        TextView timestamp;

        ConstraintLayout constraintLayout;

        public MessageReceivedViewHandler(@NonNull View itemView) {
            super(itemView);
            receivedMessage = itemView.findViewById(R.id.message_received_text);
            date = itemView.findViewById(R.id.message_thread_received_date_text);
            timestamp = itemView.findViewById(R.id.received_message_date_segment);
            constraintLayout = itemView.findViewById(R.id.message_received_constraint);
        }

        public void highlight() {
//            constraintLayout.setBackgroundColor(context.getResources().getColor(
//                    R.color.primary_highlighted_color, context.getTheme()));
//            constraintLayout.setBackgroundResource(R.drawable.received_messages_highlighted_drawable);
            constraintLayout.setBackgroundResource(0);
            constraintLayout.setBackgroundResource(R.drawable.received_messages_highlighted_drawable);
        }
    }
    public static class TimestampMessageReceivedViewHandler extends MessageReceivedViewHandler {
        public TimestampMessageReceivedViewHandler(@NonNull View itemView) {
            super(itemView);
        }
    }

}
