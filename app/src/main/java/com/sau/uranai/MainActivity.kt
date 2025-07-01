package com.sau.uranai

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.sau.uranai.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var fragmentManager: FragmentManager
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force light theme
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            // If user is not logged in, redirect to login screen
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Set up Toolbar and Navigation Drawer
        setSupportActionBar(binding.toolbar)

        // Set up Navigation Drawer Toggle
        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.nav_open,
            R.string.nav_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Set up Navigation Drawer Menu
        binding.navigationDrawer.menu.clear() // Clear any existing items
        binding.navigationDrawer.inflateMenu(R.menu.drawer_menu)
        binding.navigationDrawer.setNavigationItemSelectedListener(this)

        // Set up Bottom Navigation
        binding.bottomNavigation.background = null
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_home -> openFragment(HomeFragment())
                R.id.nav_profile -> openProfileActivity()
            }
            true
        }

        // Set up initial fragment
        fragmentManager = supportFragmentManager
        if (savedInstanceState == null) {
            openFragment(HomeFragment())
        }

        // FAB Click Listener
        binding.fab.setOnClickListener {
            Toast.makeText(this, "Take photo for Fortune", Toast.LENGTH_SHORT).show()
            openFortuneActivity()
        }
    }

    override fun onBackPressed() {
        // Close drawer on back press if it's open
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation drawer item clicks
        when (item.itemId) {
            R.id.nav_home -> {
                openFragment(HomeFragment())
                binding.bottomNavigation.selectedItemId = R.id.bottom_home
            }
            R.id.nav_fortune -> {
                openFortuneActivity()
            }
            R.id.nav_profile -> {
                openProfileActivity()
                binding.bottomNavigation.selectedItemId = R.id.nav_profile
            }

        }

        // Close the drawer after item selection
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun openFragment(fragment: Fragment) {
        val fragmentTransaction: FragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.fragment_container, fragment)
        fragmentTransaction.commit()
    }

    private fun openFortuneActivity() {
        val intent = Intent(this, FortuneActivity::class.java)
        startActivity(intent)
    }

    private fun openProfileActivity() {
        val intent = Intent(this, ProfileActivity::class.java)
        startActivity(intent)
    }
}