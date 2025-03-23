package com.sau.uranai

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val nameTextView: TextView = findViewById(R.id.textView)
        val emailTextView: TextView = findViewById(R.id.textView2)

        val myReviewsButton: Button = findViewById(R.id.button)
        val personalInfoButton: Button = findViewById(R.id.button3)
        val notificationsButton: Button = findViewById(R.id.button4)
        val accountSettingsButton: Button = findViewById(R.id.button2)
        val backToMainMenuButton: AppCompatButton = findViewById(R.id.backtomainmenu)

        // Kullanıcı bilgilerini Firebase'den çek
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val name = document.getString("name")
                        val surname = document.getString("surname")
                        val email = document.getString("username")

                        nameTextView.text = "$name $surname"
                        emailTextView.text = email
                    }
                }
                .addOnFailureListener { e ->
                    // Hata durumunda kullanıcıya bilgi ver
                    nameTextView.text = "Error loading data"
                    emailTextView.text = "Error loading data"
                }
        }

        myReviewsButton.setOnClickListener {
            Toast.makeText(this, "Coming Soon...", Toast.LENGTH_SHORT).show()
        }

        personalInfoButton.setOnClickListener {
            val intent = Intent(this, PersonalInfoActivity::class.java)
            startActivity(intent)
        }

        notificationsButton.setOnClickListener {
            Toast.makeText(this, "Coming Soon...", Toast.LENGTH_SHORT).show()
        }

        accountSettingsButton.setOnClickListener {
            val intent = Intent(this, AccountSettingsActivity::class.java)
            startActivity(intent)
        }

        backToMainMenuButton.setOnClickListener {
            backMainpage()
        }
    }

    private fun backMainpage() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}