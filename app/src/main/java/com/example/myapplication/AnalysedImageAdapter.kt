package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AnalysedImageAdapter(
    var analysedImages : List<AnalysedImage>
) : RecyclerView.Adapter<AnalysedImageAdapter.AnalysedImageViewHolder>() {

    inner class AnalysedImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val tvImageTitle : TextView
        val tvImageAnalysyis : TextView

        init {
            tvImageTitle = itemView.findViewById(R.id.tvImageName)
            tvImageAnalysyis = itemView.findViewById(R.id.tvAnalysis)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnalysedImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_analysed_image, parent, false)
        return AnalysedImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnalysedImageViewHolder, position: Int) {
        val analysedImage = analysedImages[position]
        holder.tvImageTitle.text = analysedImage.title
        holder.tvImageAnalysyis.text = analysedImage.analysis
    }

    override fun getItemCount(): Int {
        return analysedImages.size
    }
}