package com.example.sumapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class ObjectivesActivity extends AppCompatActivity {

    private TextView textViewCurrentObjective;
    private TextView textViewCurrentProgress;
    private Button buttonStopObjective;
    private RadioGroup radioGroupPeriod;
    private EditText editTextTargetArrows;
    private TextView textViewPreview;
    private Button buttonCancel;
    private Button buttonSaveObjective;

    // Clés SharedPreferences
    private static final String PREFS_NAME = "SumAppPrefs";
    private static final String KEY_OBJECTIVE_ACTIVE = "objectiveActive";
    private static final String KEY_OBJECTIVE_TYPE = "objectiveType"; // 0=semaine, 1=mois, 2=année
    private static final String KEY_OBJECTIVE_TARGET = "objectiveTarget";
    private static final String KEY_OBJECTIVE_START_DATE = "objectiveStartDate";
    private static final String KEY_OBJECTIVE_END_DATE = "objectiveEndDate";
    private static final String KEY_CURRENT_SUM = "currentSum";
    private static final String KEY_ARROW_HISTORY_PREFIX = "arrowHistory_";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_objectives);

        // Initialisation des vues
        textViewCurrentObjective = findViewById(R.id.textViewCurrentObjective);
        textViewCurrentProgress = findViewById(R.id.textViewCurrentProgress);
        buttonStopObjective = findViewById(R.id.buttonStopObjective);
        radioGroupPeriod = findViewById(R.id.radioGroupPeriod);
        editTextTargetArrows = findViewById(R.id.editTextTargetArrows);
        textViewPreview = findViewById(R.id.textViewPreview);
        buttonCancel = findViewById(R.id.buttonCancel);
        buttonSaveObjective = findViewById(R.id.buttonSaveObjective);

        // Affichage de l'objectif actuel
        updateCurrentObjectiveDisplay();

        // Écouteurs d'événements
        buttonStopObjective.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopCurrentObjective();
            }
        });

        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        buttonSaveObjective.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveNewObjective();
            }
        });

        // Mise à jour de la prévisualisation en temps réel
        TextWatcher previewWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePreview();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        editTextTargetArrows.addTextChangedListener(previewWatcher);
        radioGroupPeriod.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                updatePreview();
            }
        });

        // Mise à jour initiale de la prévisualisation
        updatePreview();
    }

    private void updateCurrentObjectiveDisplay() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean hasObjective = prefs.getBoolean(KEY_OBJECTIVE_ACTIVE, false);

        if (hasObjective) {
            int objectiveType = prefs.getInt(KEY_OBJECTIVE_TYPE, 0);
            int targetArrows = prefs.getInt(KEY_OBJECTIVE_TARGET, 0);
            String startDate = prefs.getString(KEY_OBJECTIVE_START_DATE, "");
            String endDate = prefs.getString(KEY_OBJECTIVE_END_DATE, "");

            String periodText = objectiveType == 0 ? "Semaine" : 
                               objectiveType == 1 ? "Mois" : "Année";

            textViewCurrentObjective.setText(String.format("Objectif %s : %d flèches", periodText, targetArrows));
            
            // Calculer le progrès
            int currentProgress = calculateCurrentProgress(startDate);
            int daysRemaining = calculateDaysRemaining(endDate);
            int dailyTarget = calculateDailyTarget(targetArrows, currentProgress, daysRemaining);

            String progressText = String.format(
                "Progrès : %d/%d flèches\n" +
                "Flèches par jour recommandées : %d\n" +
                "Jours restants : %d",
                currentProgress, targetArrows, dailyTarget, daysRemaining
            );

            textViewCurrentProgress.setText(progressText);
            buttonStopObjective.setVisibility(View.VISIBLE);
        } else {
            textViewCurrentObjective.setText("Aucun objectif défini");
            textViewCurrentProgress.setText("");
            buttonStopObjective.setVisibility(View.GONE);
        }
    }

    private void updatePreview() {
        String targetStr = editTextTargetArrows.getText().toString();
        if (targetStr.isEmpty()) {
            textViewPreview.setText("Saisissez vos objectifs ci-dessus");
            return;
        }

        try {
            int target = Integer.parseInt(targetStr);
            int selectedPeriod = getSelectedPeriod();
            
            // Calculer le nombre de jours réels selon la période
            Calendar startCal = Calendar.getInstance();
            Calendar endCal = Calendar.getInstance();
            int days;
            String periodDescription;
            
            if (selectedPeriod == 0) { // Semaine
                // Du lundi au dimanche de cette semaine
                int dayOfWeek = startCal.get(Calendar.DAY_OF_WEEK);
                int daysFromMonday = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
                startCal.add(Calendar.DAY_OF_YEAR, -daysFromMonday);
                endCal.setTime(startCal.getTime());
                endCal.add(Calendar.DAY_OF_YEAR, 6);
                days = 7;
                periodDescription = "cette semaine (lundi-dimanche)";
            } else if (selectedPeriod == 1) { // Mois
                // Du 1er à la fin du mois courant
                startCal.set(Calendar.DAY_OF_MONTH, 1);
                days = startCal.getActualMaximum(Calendar.DAY_OF_MONTH);
                periodDescription = "ce mois (1er-" + days + ")";
            } else { // Année
                // Du 1er janvier au 31 décembre
                startCal.set(Calendar.DAY_OF_YEAR, 1);
                days = startCal.getActualMaximum(Calendar.DAY_OF_YEAR);
                periodDescription = "cette année (1er janv-31 déc)";
            }
            
            int dailyAverage = target / days;

            String previewText = String.format(
                "Objectif : %d flèches pour %s\n" +
                "Soit environ %d flèches par jour\n" +
                "Période : %d jours",
                target, periodDescription, dailyAverage, days
            );

            textViewPreview.setText(previewText);
        } catch (NumberFormatException e) {
            textViewPreview.setText("Nombre invalide");
        }
    }

    private int getSelectedPeriod() {
        int checkedId = radioGroupPeriod.getCheckedRadioButtonId();
        if (checkedId == R.id.radioWeek) return 0;
        if (checkedId == R.id.radioMonth) return 1;
        if (checkedId == R.id.radioYear) return 2;
        return 0;
    }

    private void saveNewObjective() {
        String targetStr = editTextTargetArrows.getText().toString();
        if (targetStr.isEmpty()) {
            Toast.makeText(this, "Veuillez saisir un nombre de flèches", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int target = Integer.parseInt(targetStr);
            if (target <= 0) {
                Toast.makeText(this, "Le nombre de flèches doit être positif", Toast.LENGTH_SHORT).show();
                return;
            }

            int selectedPeriod = getSelectedPeriod();
            
            // Calculer les dates de début et fin
            Calendar startCal = Calendar.getInstance();
            Calendar endCal = Calendar.getInstance();
            
            if (selectedPeriod == 0) { // Semaine
                // Commencer au lundi de cette semaine
                int dayOfWeek = startCal.get(Calendar.DAY_OF_WEEK);
                int daysFromMonday = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
                startCal.add(Calendar.DAY_OF_YEAR, -daysFromMonday);
                endCal.setTime(startCal.getTime());
                endCal.add(Calendar.DAY_OF_YEAR, 6);
            } else if (selectedPeriod == 1) { // Mois
                // Commencer au 1er du mois
                startCal.set(Calendar.DAY_OF_MONTH, 1);
                endCal.setTime(startCal.getTime());
                endCal.add(Calendar.MONTH, 1);
                endCal.add(Calendar.DAY_OF_YEAR, -1);
            } else { // Année
                // Commencer au 1er janvier
                startCal.set(Calendar.DAY_OF_YEAR, 1);
                endCal.setTime(startCal.getTime());
                endCal.add(Calendar.YEAR, 1);
                endCal.add(Calendar.DAY_OF_YEAR, -1);
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String startDate = dateFormat.format(startCal.getTime());
            String endDate = dateFormat.format(endCal.getTime());

            // Sauvegarder l'objectif
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_OBJECTIVE_ACTIVE, true);
            editor.putInt(KEY_OBJECTIVE_TYPE, selectedPeriod);
            editor.putInt(KEY_OBJECTIVE_TARGET, target);
            editor.putString(KEY_OBJECTIVE_START_DATE, startDate);
            editor.putString(KEY_OBJECTIVE_END_DATE, endDate);
            editor.apply();

            Toast.makeText(this, "Objectif sauvegardé !", Toast.LENGTH_SHORT).show();
            updateCurrentObjectiveDisplay();

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Nombre invalide", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopCurrentObjective() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_OBJECTIVE_ACTIVE, false);
        editor.apply();

        Toast.makeText(this, "Objectif arrêté", Toast.LENGTH_SHORT).show();
        updateCurrentObjectiveDisplay();
    }

    private int calculateCurrentProgress(String startDate) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, ?> allPrefs = prefs.getAll();
        
        int totalArrows = 0;
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        
        // Compter les flèches depuis la date de début
        for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
            if (entry.getKey().startsWith(KEY_ARROW_HISTORY_PREFIX)) {
                String date = entry.getKey().substring(KEY_ARROW_HISTORY_PREFIX.length());
                if (date.compareTo(startDate) >= 0 && date.compareTo(currentDate) <= 0) {
                    totalArrows += (Integer) entry.getValue();
                }
            }
        }
        
        // Ajouter les flèches du jour actuel
        totalArrows += prefs.getInt(KEY_CURRENT_SUM, 0);
        
        return totalArrows;
    }

    private int calculateDaysRemaining(String endDate) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date end = dateFormat.parse(endDate);
            Date now = new Date();
            
            long diffInMillies = end.getTime() - now.getTime();
            int days = (int) (diffInMillies / (1000 * 60 * 60 * 24)) + 1; // +1 pour inclure aujourd'hui
            
            return Math.max(0, days);
        } catch (Exception e) {
            return 0;
        }
    }

    private int calculateDailyTarget(int totalTarget, int currentProgress, int daysRemaining) {
        if (daysRemaining <= 0) return 0;
        
        int remainingArrows = totalTarget - currentProgress;
        return Math.max(0, (int) Math.ceil((double) remainingArrows / daysRemaining));
    }
} 