package com.trajoid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.textMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = messages[position]
        holder.textView.text = msg.text
        if (msg.isUser) {
            holder.textView.setBackgroundColor(0xFFE3F2FD.toInt())
            holder.textView.layoutParams = (holder.textView.layoutParams as ViewGroup.MarginLayoutParams).apply {
                marginStart = 100
                marginEnd = 0
            }
        } else {
            holder.textView.setBackgroundColor(0xFFF5F5F5.toInt())
            holder.textView.layoutParams = (holder.textView.layoutParams as ViewGroup.MarginLayoutParams).apply {
                marginStart = 0
                marginEnd = 100
            }
        }
    }

    override fun getItemCount() = messages.size
}
