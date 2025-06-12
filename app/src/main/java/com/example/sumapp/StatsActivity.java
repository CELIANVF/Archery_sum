package com.example.sumapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.tabs.TabLayout;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class StatsActivity extends AppCompatActivity {

    private TextView textViewPeriodTotal;
    private TextView textViewPeriodAverage;
    private TextView textViewCurrentPeriod;
    private LineChart lineChart;
    private BarChart barChart;
    private Button buttonBack;
    private Button buttonPrevious;
    private Button buttonNext;
    private TabLayout tabLayout;

    private Map<String, Integer> arrowHistory = new TreeMap<>();

    // Clés pour SharedPreferences
    private static final String PREFS_NAME = "SumAppPrefs";
    private static final String KEY_ARROW_HISTORY_PREFIX = "arrowHistory_";
    private static final String KEY_CURRENT_DATE = "currentDate";
    private static final String KEY_CURRENT_SUM = "currentSum";

    // Périodes pour les onglets
    private static final int PERIOD_WEEK = 0;
    private static final int PERIOD_MONTH = 1;
    private static final int PERIOD_YEAR = 2;
    private static final int PERIOD_ALL = 3;

    private int currentPeriod = PERIOD_WEEK;
    private int periodOffset = 0; // 0 = période courante, -1 = précédente, etc.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.stats_activity);

        // Initialisation des vues
        textViewPeriodTotal = findViewById(R.id.textViewPeriodTotal);
        textViewPeriodAverage = findViewById(R.id.textViewPeriodAverage);
        textViewCurrentPeriod = findViewById(R.id.textViewCurrentPeriod);
        lineChart = findViewById(R.id.lineChart);
        barChart = findViewById(R.id.barChart);
        buttonBack = findViewById(R.id.buttonBack);
        buttonPrevious = findViewById(R.id.buttonPrevious);
        buttonNext = findViewById(R.id.buttonNext);
        tabLayout = findViewById(R.id.tabLayout);

        // Configuration du TabLayout
        tabLayout.addTab(tabLayout.newTab().setText("Semaine"));
        tabLayout.addTab(tabLayout.newTab().setText("Mois"));
        tabLayout.addTab(tabLayout.newTab().setText("Année"));
        tabLayout.addTab(tabLayout.newTab().setText("Tout"));

        // Charger les données de l'historique
        loadArrowHistory();

        // Ajouter les écouteurs d'événements
        buttonBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentPeriod = tab.getPosition();
                periodOffset = 0; // Reset à la période courante quand on change d'onglet
                updateChartsAndStats();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        // Écouteurs pour les boutons de navigation
        buttonPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                periodOffset--;
                updateChartsAndStats();
            }
        });

        buttonNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (periodOffset < 0) { // Ne peut pas aller au-delà de la période courante
                    periodOffset++;
                    updateChartsAndStats();
                }
            }
        });

        // Mettre à jour les graphiques avec la période initiale
        updateChartsAndStats();
    }

    private void loadArrowHistory() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, ?> allPrefs = prefs.getAll();

        // Charger l'historique des flèches
        for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
            if (entry.getKey().startsWith(KEY_ARROW_HISTORY_PREFIX)) {
                String date = entry.getKey().substring(KEY_ARROW_HISTORY_PREFIX.length());
                Integer count = (Integer) entry.getValue();
                arrowHistory.put(date, count);
            }
        }

        // Ajouter le jour actuel qu'il y ait des flèches ou non
        String currentDate = prefs.getString(KEY_CURRENT_DATE, getCurrentDateString());
        int currentSum = prefs.getInt(KEY_CURRENT_SUM, 0);

        // Toujours inclure le jour actuel pour les stats complètes
        arrowHistory.put(currentDate, currentSum);
    }

    private String getCurrentDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date());
    }

    private void updateChartsAndStats() {
        // Filtrer les données selon la période sélectionnée
        Map<String, Integer> filteredData = filterDataByPeriod(currentPeriod);
        
        // Mettre à jour l'affichage de la période courante
        updateCurrentPeriodDisplay();
        
        // Mettre à jour les boutons de navigation
        updateNavigationButtons();
        
        // Mettre à jour les statistiques textuelles
        updateStats(filteredData);
        
        // Mettre à jour les graphiques
        updateLineChart(filteredData);
        updateBarChart(filteredData);
    }

    private void updateCurrentPeriodDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        Calendar calendar = Calendar.getInstance();
        
        String periodDescription = "";
        
        switch (currentPeriod) {
            case PERIOD_WEEK:
                // Calculer la semaine affichée
                int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
                int daysFromMonday = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
                calendar.add(Calendar.DAY_OF_YEAR, -daysFromMonday);
                calendar.add(Calendar.WEEK_OF_YEAR, periodOffset);
                
                Date startWeek = calendar.getTime();
                calendar.add(Calendar.DAY_OF_YEAR, 6);
                Date endWeek = calendar.getTime();
                
                if (periodOffset == 0) {
                    periodDescription = "Semaine courante";
                } else if (periodOffset == -1) {
                    periodDescription = "Semaine précédente";
                } else {
                    periodDescription = "Semaine du " + sdf.format(startWeek);
                }
                break;
                
            case PERIOD_MONTH:
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                calendar.add(Calendar.MONTH, periodOffset);
                
                SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
                if (periodOffset == 0) {
                    periodDescription = "Mois courant";
                } else if (periodOffset == -1) {
                    periodDescription = "Mois précédent";
                } else {
                    periodDescription = monthFormat.format(calendar.getTime());
                }
                break;
                
            case PERIOD_YEAR:
                calendar.set(Calendar.DAY_OF_YEAR, 1);
                calendar.add(Calendar.YEAR, periodOffset);
                
                int year = calendar.get(Calendar.YEAR);
                if (periodOffset == 0) {
                    periodDescription = "Année courante";
                } else if (periodOffset == -1) {
                    periodDescription = "Année précédente";
                } else {
                    periodDescription = "Année " + year;
                }
                break;
                
            case PERIOD_ALL:
                periodDescription = "Toutes les données";
                break;
        }
        
        textViewCurrentPeriod.setText(periodDescription);
    }

    private void updateNavigationButtons() {
        // Le bouton "Suivant" est désactivé si on est à la période courante
        buttonNext.setEnabled(periodOffset < 0);
        
        // Le bouton "Précédent" est toujours activé sauf pour "Tout"
        buttonPrevious.setEnabled(currentPeriod != PERIOD_ALL);
        
        // Masquer les boutons pour "Tout"
        if (currentPeriod == PERIOD_ALL) {
            buttonPrevious.setVisibility(View.GONE);
            buttonNext.setVisibility(View.GONE);
        } else {
            buttonPrevious.setVisibility(View.VISIBLE);
            buttonNext.setVisibility(View.VISIBLE);
        }
    }

    private Map<String, Integer> filterDataByPeriod(int period) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar calendar = Calendar.getInstance();
        
        Date startDate;
        Date endDate;

        switch (period) {
            case PERIOD_WEEK:
                // Aller au lundi de la semaine (courante + offset)
                int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
                int daysFromMonday = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
                calendar.add(Calendar.DAY_OF_YEAR, -daysFromMonday);
                // Appliquer l'offset (en semaines)
                calendar.add(Calendar.WEEK_OF_YEAR, periodOffset);
                startDate = calendar.getTime();
                
                // Aller au dimanche de cette semaine
                calendar.add(Calendar.DAY_OF_YEAR, 6);
                endDate = calendar.getTime();
                break;
                
            case PERIOD_MONTH:
                // Aller au 1er du mois (courant + offset)
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                // Appliquer l'offset (en mois)
                calendar.add(Calendar.MONTH, periodOffset);
                startDate = calendar.getTime();
                
                // Aller au dernier jour de ce mois
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
                endDate = calendar.getTime();
                break;
                
            case PERIOD_YEAR:
                // Aller au 1er janvier (année courante + offset)
                calendar.set(Calendar.DAY_OF_YEAR, 1);
                // Appliquer l'offset (en années)
                calendar.add(Calendar.YEAR, periodOffset);
                startDate = calendar.getTime();
                
                // Aller au dernier jour de cette année
                calendar.set(Calendar.DAY_OF_YEAR, calendar.getActualMaximum(Calendar.DAY_OF_YEAR));
                endDate = calendar.getTime();
                break;
                
            default:
                // Toutes les données
                return new TreeMap<>(arrowHistory);
        }

        Map<String, Integer> filteredData = new TreeMap<>();
        
        // Créer une plage complète de dates pour la période
        Calendar dateIterator = Calendar.getInstance();
        dateIterator.setTime(startDate);
        Calendar endCalendar = Calendar.getInstance();
        endCalendar.setTime(endDate);
        
        while (!dateIterator.after(endCalendar)) {
            String dateString = sdf.format(dateIterator.getTime());
            // Utiliser les données existantes ou 0 si aucune donnée pour ce jour
            int arrowCount = arrowHistory.getOrDefault(dateString, 0);
            filteredData.put(dateString, arrowCount);
            dateIterator.add(Calendar.DAY_OF_YEAR, 1);
        }

        return filteredData;
    }

    private void updateStats(Map<String, Integer> filteredData) {
        int total = 0;
        for (int value : filteredData.values()) {
            total += value;
        }
        
        // Calculer la moyenne quotidienne sur tous les jours de la période (y compris les jours à 0)
        int numberOfDays = filteredData.size();
        float average = numberOfDays > 0 ? (float) total / numberOfDays : 0;
        
        // Calculer quelques statistiques supplémentaires
        int daysWithArrows = 0;
        int maxArrows = 0;
        for (int value : filteredData.values()) {
            if (value > 0) {
                daysWithArrows++;
            }
            if (value > maxArrows) {
                maxArrows = value;
            }
        }
        
        // Mettre à jour les vues
        textViewPeriodTotal.setText("Total de la période : " + total + " flèches");
        String avgText = String.format(Locale.getDefault(), "Moyenne quotidienne : %.1f flèches/jour\n" +
                "Jours avec flèches : %d/%d\n" +
                "Maximum en un jour : %d flèches", 
                average, daysWithArrows, numberOfDays, maxArrows);
        textViewPeriodAverage.setText(avgText);
    }

    private void updateLineChart(Map<String, Integer> filteredData) {
        List<Entry> entries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        
        // Convertir les dates en un format court pour l'affichage
        SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM", Locale.getDefault());
        SimpleDateFormat parseFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        
        int i = 0;
        for (Map.Entry<String, Integer> entry : filteredData.entrySet()) {
            entries.add(new Entry(i, entry.getValue()));
            
            // Formater la date pour l'affichage
            try {
                Date date = parseFormat.parse(entry.getKey());
                if (date != null) {
                    labels.add(displayFormat.format(date));
                } else {
                    labels.add(entry.getKey());
                }
            } catch (ParseException e) {
                labels.add(entry.getKey());
            }
            
            i++;
        }

        // Configurer le dataset et le graphique
        LineDataSet dataSet = new LineDataSet(entries, "Flèches tirées");
        dataSet.setColor(Color.BLUE);
        dataSet.setCircleColor(Color.BLUE);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(true);
        
        LineData lineData = new LineData(dataSet);
        
        // Configurer les axes
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(45f);
        
        // Configurer l'axe Y pour commencer à 0
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setGranularity(1f);
        
        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setEnabled(false); // Désactiver l'axe de droite
        
        lineChart.getDescription().setEnabled(false);
        lineChart.setData(lineData);
        lineChart.invalidate();
    }

    private void updateBarChart(Map<String, Integer> filteredData) {
        List<BarEntry> entries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        
        // Convertir les dates en un format court pour l'affichage
        SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM", Locale.getDefault());
        SimpleDateFormat parseFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        
        int i = 0;
        for (Map.Entry<String, Integer> entry : filteredData.entrySet()) {
            entries.add(new BarEntry(i, entry.getValue()));
            
            // Formater la date pour l'affichage
            try {
                Date date = parseFormat.parse(entry.getKey());
                if (date != null) {
                    labels.add(displayFormat.format(date));
                } else {
                    labels.add(entry.getKey());
                }
            } catch (ParseException e) {
                labels.add(entry.getKey());
            }
            
            i++;
        }

        // Configurer le dataset et le graphique
        BarDataSet dataSet = new BarDataSet(entries, "Flèches tirées");
        dataSet.setColor(Color.GREEN);
        
        BarData barData = new BarData(dataSet);
        
        // Configurer les axes
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(45f);
        
        // Configurer l'axe Y pour commencer à 0
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setGranularity(1f);
        
        YAxis rightAxis = barChart.getAxisRight();
        rightAxis.setEnabled(false); // Désactiver l'axe de droite
        
        barChart.getDescription().setEnabled(false);
        barChart.setData(barData);
        barChart.invalidate();
    }
} 