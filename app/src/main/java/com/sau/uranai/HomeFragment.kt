package com.sau.uranai

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.sau.uranai.FortuneActivity
import com.sau.uranai.R

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        setupCardViewListeners(view)
        return view
    }

    private fun setupCardViewListeners(view: View) {
        // Kahve Falı CardView
        view.findViewById<CardView>(R.id.kahve_cardView).setOnClickListener {
            startActivity(Intent(activity, FortuneActivity::class.java))
        }

        // El Falı CardView
        view.findViewById<CardView>(R.id.elfali_cardView).setOnClickListener {
            showToastMessage()
        }

        // Tarot Falı CardView
        view.findViewById<CardView>(R.id.tarot_cardView).setOnClickListener {
            showToastMessage()
        }

    }
    private fun showToastMessage() {
        Toast.makeText(requireContext(), "Coming Soon...", Toast.LENGTH_SHORT).show()
    }
}
