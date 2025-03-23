package com.sau.uranai

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AccountSettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_settings)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val usernameEditText: EditText = findViewById(R.id.usernameEditText)
        val currentPasswordEditText: EditText = findViewById(R.id.currentPasswordEditText)
        val newPasswordEditText: EditText = findViewById(R.id.newPasswordEditText)
        val confirmNewPasswordEditText: EditText = findViewById(R.id.confirmNewPasswordEditText)
        val updateAccountSettingsButton: Button = findViewById(R.id.updateAccountSettingsButton)
        val deleteAccountButton: Button = findViewById(R.id.deleteAccountButton)

        updateAccountSettingsButton.setOnClickListener {
            val newUsername = usernameEditText.text.toString()
            val currentPassword = currentPasswordEditText.text.toString()
            val newPassword = newPasswordEditText.text.toString()
            val confirmNewPassword = confirmNewPasswordEditText.text.toString()

            if (newUsername.isEmpty() || currentPassword.isEmpty() || newPassword.isEmpty() || confirmNewPassword.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword != confirmNewPassword) {
                Toast.makeText(this, "New password and confirm password do not match.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val user = auth.currentUser
            if (user != null) {
                val credential = EmailAuthProvider
                    .getCredential("${user.email}", currentPassword)

                user.reauthenticate(credential)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // Kullanıcı doğrulandı, şimdi şifreyi güncelleyebiliriz
                            if (newPassword.isNotEmpty()) {
                                user.updatePassword(newPassword)
                                    .addOnCompleteListener { passwordTask ->
                                        if (passwordTask.isSuccessful) {
                                            // Firebase Authentication'da şifre güncellendi
                                            updateUsernameInFirestore(newUsername)
                                            Toast.makeText(this, "Account settings updated successfully.", Toast.LENGTH_SHORT).show()
                                            finish()
                                        } else {
                                            Toast.makeText(this, "Failed to update password: ${passwordTask.exception?.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            } else {
                                updateUsernameInFirestore(newUsername)
                                Toast.makeText(this, "Username updated successfully.", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        } else {
                            Toast.makeText(this, "Re-authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }

        deleteAccountButton.setOnClickListener {
            val currentPassword = currentPasswordEditText.text.toString()

            if (currentPassword.isEmpty()) {
                Toast.makeText(this, "Please enter your current password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val user = auth.currentUser
            if (user != null) {
                val credential = EmailAuthProvider
                    .getCredential("${user.email}", currentPassword)

                user.reauthenticate(credential)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            user.delete()
                                .addOnCompleteListener { deleteTask ->
                                    if (deleteTask.isSuccessful) {
                                        deleteUserFromFirestore()
                                        Toast.makeText(this, "Account deleted successfully.", Toast.LENGTH_SHORT).show()
                                        val intent = Intent(this, LoginActivity::class.java)
                                        startActivity(intent)
                                        finish()
                                    } else {
                                        Toast.makeText(this, "Failed to delete account: ${deleteTask.exception?.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        } else {
                            Toast.makeText(this, "Re-authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
    }

    private fun updateUsernameInFirestore(newUsername: String) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId)
                .update("username", newUsername)
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to update username in Firestore: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun deleteUserFromFirestore() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId)
                .delete()
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to delete user from Firestore: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}