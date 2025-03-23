package com.sau.uranai

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val nameEditText: EditText = findViewById(R.id.nameEditText)
        val surnameEditText: EditText = findViewById(R.id.surnameEditText)
        val genderSpinner: Spinner = findViewById(R.id.genderSpinner)
        val birthDateEditText: EditText = findViewById(R.id.birthDateEditText)
        val zodiacSpinner: Spinner = findViewById(R.id.zodiacSpinner)
        val citySpinner: Spinner = findViewById(R.id.citySpinner)
        val occupationEditText: EditText = findViewById(R.id.occupationEditText)
        val maritalStatusSpinner: Spinner = findViewById(R.id.maritalStatusSpinner)
        val usernameEditText: EditText = findViewById(R.id.usernameEditText)
        val passwordEditText: EditText = findViewById(R.id.passwordEditText)
        val registerButton: Button = findViewById(R.id.registerButton)

        // Cinsiyet, Burç, Şehir ve Medeni Durum için Spinner'ları doldur
        ArrayAdapter.createFromResource(
            this,
            R.array.gender_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            genderSpinner.adapter = adapter
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.zodiac_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            zodiacSpinner.adapter = adapter
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.city_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            citySpinner.adapter = adapter
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.marital_status_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            maritalStatusSpinner.adapter = adapter
        }

        // Doğum tarihi için DatePickerDialog
        birthDateEditText.setOnClickListener {
            val c = Calendar.getInstance()
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)

            val dpd = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                val selectedDate = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                birthDateEditText.setText(selectedDate)
            }, year, month, day)

            dpd.show()
        }

        registerButton.setOnClickListener {
            val name = nameEditText.text.toString()
            val surname = surnameEditText.text.toString()
            val gender = genderSpinner.selectedItem.toString()
            val birthDate = birthDateEditText.text.toString()
            val zodiac = zodiacSpinner.selectedItem.toString()
            val city = citySpinner.selectedItem.toString()
            val occupation = occupationEditText.text.toString()
            val maritalStatus = maritalStatusSpinner.selectedItem.toString()
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (name.isEmpty() || surname.isEmpty() || gender.isEmpty() || birthDate.isEmpty() || zodiac.isEmpty() || city.isEmpty() || occupation.isEmpty() || maritalStatus.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Lütfen tüm alanları doldurun.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Şifre en az 6 karakter uzunluğunda olmalıdır.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword("$username@example.com", password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid
                        val user = hashMapOf(
                            "name" to name,
                            "surname" to surname,
                            "gender" to gender,
                            "birthDate" to birthDate,
                            "zodiac" to zodiac,
                            "city" to city,
                            "occupation" to occupation,
                            "maritalStatus" to maritalStatus,
                            "username" to username
                        )

                        if (userId != null) {
                            db.collection("users").document(userId)
                                .set(user)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Kayıt başarılı.", Toast.LENGTH_SHORT).show()
                                    val intent = Intent(this, MainActivity::class.java)
                                    startActivity(intent)
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Veritabanına kayıt başarısız: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } else {
                        Toast.makeText(this, "Kayıt başarısız. Hata: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }
}