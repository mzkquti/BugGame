package com.yourname.buggame

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)

        val titles = listOf("Регистрация", "Игра", "Правила", "Авторы", "Настройки")

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = titles.size
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> RegistrationFragment()
                1 -> GameFragment()
                2 -> RulesFragment()
                3 -> AuthorsFragment()
                else -> SettingsFragment()
            }
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = titles[position]
        }.attach()

        // Игровое меню: переход на нужную вкладку
        toolbar.inflateMenu(R.menu.menu_main)
        toolbar.setOnMenuItemClickListener { item ->
            val page = when (item.itemId) {
                R.id.menu_game -> 1
                R.id.menu_rules -> 2
                R.id.menu_authors -> 3
                R.id.menu_settings -> 4
                else -> return@setOnMenuItemClickListener false
            }
            viewPager.currentItem = page
            true
        }
    }
}