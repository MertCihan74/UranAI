package com.sau.uranai

import android.app.DatePickerDialog
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

class PersonalInfoActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personal_info)

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
        val updateButton: Button = findViewById(R.id.updateButton)

        // Cinsiyet, Burç, Şehir ve Medeni Durum için Spinner'ları doldur
        val genderAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.gender_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            genderSpinner.adapter = adapter
        }

        val zodiacAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.zodiac_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            zodiacSpinner.adapter = adapter
        }

        val cityAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.city_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            citySpinner.adapter = adapter
        }

        val maritalStatusAdapter = ArrayAdapter.createFromResource(
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

        // Kullanıcı bilgilerini Firebase'den çek ve ekrana yansıt
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        nameEditText.setText(document.getString("name"))
                        surnameEditText.setText(document.getString("surname"))
                        genderSpinner.setSelection(genderAdapter.getPosition(document.getString("gender")))
                        birthDateEditText.setText(document.getString("birthDate"))
                        zodiacSpinner.setSelection(zodiacAdapter.getPosition(document.getString("zodiac")))
                        citySpinner.setSelection(cityAdapter.getPosition(document.getString("city")))
                        occupationEditText.setText(document.getString("occupation"))
                        maritalStatusSpinner.setSelection(maritalStatusAdapter.getPosition(document.getString("maritalStatus")))
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        updateButton.setOnClickListener {
            val name = nameEditText.text.toString()
            val surname = surnameEditText.text.toString()
            val gender = genderSpinner.selectedItem.toString()
            val birthDate = birthDateEditText.text.toString()
            val zodiac = zodiacSpinner.selectedItem.toString()
            val city = citySpinner.selectedItem.toString()
            val occupation = occupationEditText.text.toString()
            val maritalStatus = maritalStatusSpinner.selectedItem.toString()

            if (name.isEmpty() || surname.isEmpty() || gender.isEmpty() || birthDate.isEmpty() || zodiac.isEmpty() || city.isEmpty() || occupation.isEmpty() || maritalStatus.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (userId != null) {
                val user = hashMapOf(
                    "name" to name,
                    "surname" to surname,
                    "gender" to gender,
                    "birthDate" to birthDate,
                    "zodiac" to zodiac,
                    "city" to city,
                    "occupation" to occupation,
                    "maritalStatus" to maritalStatus
                )

                db.collection("users").document(userId)
                    .set(user)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Information updated successfully.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to update information: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }
}