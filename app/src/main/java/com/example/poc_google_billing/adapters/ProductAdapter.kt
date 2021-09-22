package com.example.poc_google_billing.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sample.poc_google_billing.R

class ProductAdapter(private val items: MutableList<Product>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>(), View.OnClickListener {
    var listener: View.OnClickListener? = null

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val itemView = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.product_row, viewGroup, false)
        itemView.setOnClickListener(this)
        return Holder(itemView)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val holder = holder as Holder
        holder.bin(items[position])
    }

    override fun getItemCount(): Int {
        return items.size
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var txtName: TextView = itemView.findViewById(R.id.txtName)
        var txtPrice: TextView = itemView.findViewById(R.id.txtPrice)
        var btnBuy: Button = itemView.findViewById(R.id.btnBuy)

        fun bin(item: Product) {
            txtName.text = item.name
            txtPrice.text = item.price

            btnBuy.setOnClickListener { adapterListener?.onClick(item) }
        }
    }

    fun setAdapterListener(p0: AdapterListener){
        adapterListener = p0
    }

    fun setOnClickListener(listener: View.OnClickListener?) {
        this.listener = listener
    }

    override fun onClick(view: View) {
        if (listener != null) {
            if (view.tag == R.id.btnBuy) {
                listener!!.onClick(view)
            }
        }
    }

    interface AdapterListener {
        fun onClick(item: Product)
    }

    companion object {
        var adapterListener: AdapterListener? = null
    }
}

class Product(var name: String, var sku: String, var price: String)