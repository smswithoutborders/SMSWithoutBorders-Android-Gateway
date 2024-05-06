package com.afkanerd.deku.Router.GatewayServers

import android.content.Context
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import com.afkanerd.deku.DefaultSMS.Commons.Helpers
import com.afkanerd.deku.DefaultSMS.Models.Database.Datastore
import com.afkanerd.deku.DefaultSMS.Models.ThreadingPoolExecutor
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.Router.Models.RouterHandler
import com.afkanerd.deku.Router.Models.RouterItem
import com.afkanerd.deku.Router.SMTP
import com.google.android.material.card.MaterialCardView

class GatewayServerRouterRecyclerAdapter :
        RecyclerView.Adapter<GatewayServerRouterRecyclerAdapter.ViewHolder>() {

    val mDiffer = AsyncListDiffer(this, RouterItem.DIFF_CALLBACK)

    val selectedItems: MutableLiveData<HashMap<Long, ViewHolder>> = MutableLiveData()

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.gateway_server_routed_messages_layout, parent,
                false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val routedMessage: Pair<RouterItem, GatewayServer> = mDiffer.currentList[position]
        holder.bind(routedMessage)
    }

    override fun getItemCount(): Int {
        return mDiffer.currentList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var materialCardView: MaterialCardView
        private var address: TextView
        private var url: TextView
        private var body: TextView
        private var status: TextView
        private var date: TextView

        init {
            address = itemView.findViewById(R.id.routed_messages_address)
            url = itemView.findViewById(R.id.routed_messages_url)
            body = itemView.findViewById(R.id.routed_messages_body)
            status = itemView.findViewById(R.id.routed_messages_status)
            date = itemView.findViewById(R.id.routed_messages_date)
            materialCardView = itemView.findViewById(R.id.routed_messages_material_cardview)
        }

        fun bind(conversationGatewayServerPair: Pair<RouterItem, GatewayServer>) {
            val conversation = conversationGatewayServerPair.first
            val gatewayServer = conversationGatewayServerPair.second
            val gatewayServerUrl = when(gatewayServer.getProtocol()) {
                SMTP.PROTOCOL -> gatewayServer.smtp.smtp_host
                else -> gatewayServer.url
            }
            val protocolAddress = gatewayServer.getProtocol() + "/" + conversation.getAddress()
            address.text = protocolAddress
            url.text = gatewayServerUrl
            body.text = conversation.getText()
            status.text = conversation.routingStatus
            date.text = Helpers.formatDate(itemView.context, conversation.getDate().toLong())
        }
    }
}
