package com.afkanerd.deku.DefaultSMS.AdaptersViewModels

import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.Telephony
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.liveData
import com.afkanerd.deku.Datastore
import com.afkanerd.deku.DefaultSMS.Models.Conversations.Conversation
import com.afkanerd.deku.DefaultSMS.Models.NativeSMSDB
import com.afkanerd.deku.DefaultSMS.Models.SMSDatabaseWrapper
import com.afkanerd.deku.DefaultSMS.ui.InboxType
import java.util.ArrayList
import java.util.Locale
import kotlin.concurrent.thread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.afkanerd.deku.DefaultSMS.Commons.Helpers
import com.afkanerd.deku.DefaultSMS.Models.Contacts
import com.afkanerd.deku.DefaultSMS.Models.Conversations.ThreadedConversations
import com.afkanerd.deku.DefaultSMS.Models.ThreadsCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class ConversationsViewModel : ViewModel() {
//    var threadId by mutableStateOf("")
    var threadId by mutableStateOf("")
    var address by mutableStateOf("")
    var text by mutableStateOf("")
    var searchQuery by mutableStateOf("")
    var contactName: String by mutableStateOf("")
    var subscriptionId: Int by mutableIntStateOf(-1)

    var selectedItems = mutableStateListOf<String>()
    var retryDeleteItem: MutableList<Conversation> = arrayListOf()

    var liveData: LiveData<MutableList<Conversation>>? = null
    var threadedLiveData: LiveData<MutableList<Conversation>>? = null
    var archivedLiveData: LiveData<MutableList<Conversation>>? = null
    var encryptedLiveData: LiveData<MutableList<Conversation>>? = null
    var blockedLiveData: LiveData<MutableList<Conversation>>? = null
    var mutedLiveData: LiveData<MutableList<Conversation>>? = null
    var draftsLiveData: LiveData<MutableList<Conversation>>? = null

    var inboxType: InboxType = InboxType.INBOX

    fun getThreading(context: Context): LiveData<MutableList<Conversation>> {
        if(threadedLiveData == null) {
            threadedLiveData = Datastore.getDatastore(context).conversationDao().getAllThreading()
            archivedLiveData = Datastore.getDatastore(context).conversationDao().getAllThreadingArchived()
            encryptedLiveData = Datastore.getDatastore(context).conversationDao().getAllThreadingEncrypted()
            blockedLiveData = Datastore.getDatastore(context).conversationDao().getAllThreadingBlocked()
            mutedLiveData = Datastore.getDatastore(context).conversationDao().getAllThreadingMuted()
            draftsLiveData = Datastore.getDatastore(context).conversationDao().getAllThreadingDrafts()
        }
        return threadedLiveData!!
    }

    fun getLiveData(context: Context): LiveData<MutableList<Conversation>>? {
        if(liveData == null) {
            liveData = MutableLiveData()
            liveData = Datastore.getDatastore(context).conversationDao().getLiveData(threadId)
        }
        return liveData
    }

    fun insert(context: Context, conversation: Conversation): Long {
        Datastore.getDatastore(context).threadedConversationsDao()
            .insertThreadAndConversation(context, conversation)
        return 0
    }

    fun update(context: Context, conversation: Conversation) {
        Datastore.getDatastore(context).conversationDao()._update(conversation)
    }

    fun deleteItems(context: Context, conversations: List<Conversation>) {
        val datastore = Datastore.getDatastore(context)
        datastore.conversationDao().delete(conversations)
        val ids = arrayOfNulls<String>(conversations.size)
        for (i in conversations.indices) ids[i] = conversations[i].message_id
        NativeSMSDB.deleteMultipleMessages(context, ids)
    }

    fun getUnreadCount(context: Context, threadId: String) : Int {
        return Datastore.getDatastore(context).conversationDao().getUnreadCount(threadId)
    }


    fun fetchDraft(context: Context): Conversation? {
        return Datastore.getDatastore(context).conversationDao().fetchTypedConversation(
            Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT, threadId
        )
    }

    fun clearDraft(context: Context) {
        Datastore.getDatastore(context).conversationDao()
            .deleteAllType(context, Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT, threadId)
        SMSDatabaseWrapper.deleteDraft(context, threadId)
    }

    fun unMute(context: Context) {
        Datastore.getDatastore(context).conversationDao().unMute(threadId)
    }

    fun mute(context: Context) {
        Datastore.getDatastore(context).conversationDao().mute(threadId)
    }

    fun block(context: Context) {
        Datastore.getDatastore(context).conversationDao().block(threadId)
    }

    fun unBlock(context: Context) {
        Datastore.getDatastore(context).conversationDao().unBlock(threadId)
    }

    fun archive(context: Context, threadIds: List<String>) {
        Datastore.getDatastore(context).conversationDao().archive(threadIds)
    }

    fun archive(context: Context, threadId: String? = null) {
        Datastore.getDatastore(context).conversationDao().archive(threadId ?: this.threadId)
    }

    fun unArchive(context: Context, threadIds: List<String>) {
        Datastore.getDatastore(context).conversationDao().unarchive(threadIds)
    }

    fun unArchive(context: Context, threadId: String? = null) {
        Datastore.getDatastore(context).conversationDao().unarchive(threadId ?: this.threadId)
    }

    fun setSecured(context: Context, isSecured: Boolean) {
        Datastore.getDatastore(context).conversationDao().archive(threadId)
    }

    fun delete(context: Context) {
        Datastore.getDatastore(context).conversationDao().delete(threadId)
    }

    fun delete(context: Context, threadIds: List<String>) {
        Datastore.getDatastore(context).conversationDao().deleteAll(threadIds)
    }

    fun insertDraft(context: Context) {

        val conversation = Conversation();
        conversation.message_id = "1"
        conversation.thread_id = threadId
        conversation.text = text
        conversation.isRead = true
        conversation.type = Telephony.Sms.MESSAGE_TYPE_DRAFT
        conversation.date = System.currentTimeMillis().toString()
        conversation.address = address
        conversation.status = Telephony.Sms.STATUS_PENDING

        insert(context, conversation);
        SMSDatabaseWrapper.saveDraft(context, conversation);
    }

    private var folderMetrics: MutableLiveData<ThreadsCount> = MutableLiveData()
    fun getCount(context: Context) : MutableLiveData<ThreadsCount> {
        val databaseConnector = Datastore.getDatastore(context)
        CoroutineScope(Dispatchers.Default).launch {
            folderMetrics.postValue(databaseConnector.conversationDao().getFullCounts())
        }
        return folderMetrics
    }

    fun updateToRead(context: Context) {
        Datastore.getDatastore(context).conversationDao().updateRead(true, threadId)
    }

    fun unblock(context: Context) {
        BlockedNumberContract.unblock(context, this.address)
    }

    fun unblock(context: Context, addresses: List<String>) {
        for (address in addresses) {
            BlockedNumberContract.unblock(context, address)
        }
    }
}
