package com.example.edgedemo;

import android.os.Bundle;

import com.example.edgedemo.core.Devicelet;
import com.example.edgedemo.core.Edgeless;
import com.example.edgedemo.core.Starter;
import com.example.edgedemo.core.SystemUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(navView, navController);

        new Thread(new Runnable(){
            @Override
            public void run() {
                try {
                    Devicelet let = new Devicelet(SystemUtils.getIMEI(getApplicationContext()));
                    Edgeless less = new Edgeless(let);
                    Thread thread = new Thread(less);
                    thread.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();


    }

}