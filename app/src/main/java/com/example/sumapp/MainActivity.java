package com.example.sumapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.FileProvider;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private EditText editTextNumber;
    private EditText editTextScores;
    private TextView textViewSum;
    private TextView textViewAverageScore;
    private TextView textViewLastSum;
    private TextView textViewDuration;
    private TextView textViewScorePreview;
    private TextView textViewModeInfo;
    private TextView textViewObjectiveProgress;
    private TextView textViewDailyGoal;
    private Button buttonAdd;
    private Button buttonUndo;
    private Button buttonViewStats;
    private Button buttonExportCsv;
    private Button buttonImportCsv;
    private Button buttonObjectives;
    private LinearLayout historyContainer;
    private ScrollView historyScrollView;
    private LinearLayout layoutCountingMode;
    private LinearLayout layoutScoringMode;
    private LinearLayout layoutScoreInfo;
    private LinearLayout layoutObjective;
    private SwitchCompat switchMode;

    private int currentSum = 0;
    private int lastSum = 0;
    private long lastResetTime = 0;
    private String currentDate;
    private Map<String, Integer> arrowHistory = new TreeMap<>();
    private int lastAddedValue = 0; // Valeur du dernier ajout
    private boolean isScoringMode = false;
    private float totalScoreSum = 0.0f;
    private int totalScoreCount = 0;

    // Clés pour SharedPreferences
    private static final String PREFS_NAME = "SumAppPrefs";
    private static final String KEY_CURRENT_SUM = "currentSum";
    private static final String KEY_LAST_SUM = "lastSum";
    private static final String KEY_LAST_RESET_TIME = "lastResetTime";
    private static final String KEY_CURRENT_DATE = "currentDate";
    private static final String KEY_ARROW_HISTORY_PREFIX = "arrowHistory_";
    private static final String KEY_LAST_ADDED_VALUE = "lastAddedValue";
    private static final String KEY_SCORING_MODE = "scoringMode";
    private static final String KEY_TOTAL_SCORE_SUM = "totalScoreSum";
    private static final String KEY_TOTAL_SCORE_COUNT = "totalScoreCount";
    
    // Clés pour les objectifs
    private static final String KEY_OBJECTIVE_ACTIVE = "objectiveActive";
    private static final String KEY_OBJECTIVE_TARGET = "objectiveTarget";
    private static final String KEY_OBJECTIVE_START_DATE = "objectiveStartDate";
    private static final String KEY_OBJECTIVE_END_DATE = "objectiveEndDate";

    // Lanceur pour sélectionner un fichier CSV
    private ActivityResultLauncher<Intent> csvFileLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialisation des vues
        editTextNumber = findViewById(R.id.editTextNumber);
        editTextScores = findViewById(R.id.editTextScores);
        textViewSum = findViewById(R.id.textViewSum);
        textViewAverageScore = findViewById(R.id.textViewAverageScore);
        textViewLastSum = findViewById(R.id.textViewLastSum);
        textViewDuration = findViewById(R.id.textViewDuration);
        textViewScorePreview = findViewById(R.id.textViewScorePreview);
        textViewModeInfo = findViewById(R.id.textViewModeInfo);
        textViewObjectiveProgress = findViewById(R.id.textViewObjectiveProgress);
        textViewDailyGoal = findViewById(R.id.textViewDailyGoal);
        buttonAdd = findViewById(R.id.buttonAdd);
        buttonUndo = findViewById(R.id.buttonUndo);
        buttonViewStats = findViewById(R.id.buttonViewStats);
        buttonExportCsv = findViewById(R.id.buttonExportCsv);
        buttonImportCsv = findViewById(R.id.buttonImportCsv);
        buttonObjectives = findViewById(R.id.buttonObjectives);
        historyContainer = findViewById(R.id.historyContainer);
        historyScrollView = findViewById(R.id.historyScrollView);
        layoutCountingMode = findViewById(R.id.layoutCountingMode);
        layoutScoringMode = findViewById(R.id.layoutScoringMode);
        layoutScoreInfo = findViewById(R.id.layoutScoreInfo);
        layoutObjective = findViewById(R.id.layoutObjective);
        switchMode = findViewById(R.id.switchMode);

        // Initialiser le lanceur pour sélectionner un fichier CSV
        csvFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri csvUri = result.getData().getData();
                        if (csvUri != null) {
                            processCsvFile(csvUri);
                        }
                    }
                });

        // Charger les données sauvegardées
        loadSavedData();

        // Vérifier si nous sommes sur un nouveau jour
        checkDailyReset();

        // S'assurer que le jour actuel est dans l'historique (même à 0)
        arrowHistory.put(currentDate, currentSum);

        // Mettre à jour l'interface utilisateur
        updateUI();

        // Configurer les écouteurs d'événements
        buttonAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNumber();
            }
        });

        buttonUndo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                undoLastAdd();
            }
        });

        buttonViewStats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openStatsActivity();
            }
        });

        buttonExportCsv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportDataToCsv();
            }
        });

        buttonImportCsv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importDataFromCsv();
            }
        });

        buttonObjectives.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openObjectivesActivity();
            }
        });

        // Écouteur pour le switch mode
        switchMode.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                isScoringMode = isChecked;
                updateModeUI();
                saveData();
            }
        });

        // Écouteur pour la prévisualisation des scores
        editTextScores.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateScorePreview();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // Mettre à jour l'interface selon le mode
        updateModeUI();

        // Démarrer la mise à jour périodique de la durée
        startDurationUpdates();
    }

    private void loadSavedData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentSum = prefs.getInt(KEY_CURRENT_SUM, 0);
        lastSum = prefs.getInt(KEY_LAST_SUM, 0);
        lastResetTime = prefs.getLong(KEY_LAST_RESET_TIME, SystemClock.elapsedRealtime());
        currentDate = prefs.getString(KEY_CURRENT_DATE, getCurrentDateString());
        lastAddedValue = prefs.getInt(KEY_LAST_ADDED_VALUE, 0);
        isScoringMode = prefs.getBoolean(KEY_SCORING_MODE, false);
        totalScoreSum = prefs.getFloat(KEY_TOTAL_SCORE_SUM, 0.0f);
        totalScoreCount = prefs.getInt(KEY_TOTAL_SCORE_COUNT, 0);
        
        // Mettre à jour le switch
        switchMode.setChecked(isScoringMode);
        
        // Charger l'historique
        Map<String, ?> allPrefs = prefs.getAll();
        for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
            if (entry.getKey().startsWith(KEY_ARROW_HISTORY_PREFIX)) {
                String date = entry.getKey().substring(KEY_ARROW_HISTORY_PREFIX.length());
                Integer count = (Integer) entry.getValue();
                arrowHistory.put(date, count);
            }
        }
    }

    private void saveData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_CURRENT_SUM, currentSum);
        editor.putInt(KEY_LAST_SUM, lastSum);
        editor.putLong(KEY_LAST_RESET_TIME, lastResetTime);
        editor.putString(KEY_CURRENT_DATE, currentDate);
        editor.putInt(KEY_LAST_ADDED_VALUE, lastAddedValue);
        editor.putBoolean(KEY_SCORING_MODE, isScoringMode);
        editor.putFloat(KEY_TOTAL_SCORE_SUM, totalScoreSum);
        editor.putInt(KEY_TOTAL_SCORE_COUNT, totalScoreCount);
        
        // Enregistrer l'historique
        for (Map.Entry<String, Integer> entry : arrowHistory.entrySet()) {
            editor.putInt(KEY_ARROW_HISTORY_PREFIX + entry.getKey(), entry.getValue());
        }
        
        editor.apply();
    }

    private String getCurrentDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date());
    }

    private void checkDailyReset() {
        String today = getCurrentDateString();
        if (!today.equals(currentDate)) {
            // Sauvegarder le total du jour précédent dans l'historique
            // Même si c'est 0, on l'enregistre pour les stats
            arrowHistory.put(currentDate, currentSum);
            
            // Réinitialiser pour le nouveau jour
            lastSum = currentSum;
            currentSum = 0;
            lastAddedValue = 0;
            lastResetTime = SystemClock.elapsedRealtime();
            currentDate = today;
            
            // Réinitialiser les scores du jour
            totalScoreSum = 0.0f;
            totalScoreCount = 0;
            
            // Notification à l'utilisateur seulement si le compteur était positif
            if (lastSum > 0) {
                Toast.makeText(this, "Nouveau jour ! Compteur remis à zéro.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void addNumber() {
        if (isScoringMode) {
            addScores();
        } else {
            addArrowCount();
        }
    }

    private void addArrowCount() {
        String input = editTextNumber.getText().toString();
        if (TextUtils.isEmpty(input)) {
            Toast.makeText(this, "Veuillez entrer un nombre", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int number = Integer.parseInt(input);
            currentSum += number;
            lastAddedValue = number; // Sauvegarde le dernier nombre ajouté
            updateUI();
            saveData();
            editTextNumber.setText(""); // Effacer le champ après l'ajout
            
            // Mettre à jour l'état du bouton annuler
            buttonUndo.setEnabled(lastAddedValue > 0);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Veuillez entrer un nombre entier valide", Toast.LENGTH_SHORT).show();
        }
    }

    private void addScores() {
        String input = editTextScores.getText().toString();
        if (TextUtils.isEmpty(input)) {
            Toast.makeText(this, "Veuillez entrer les scores", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String[] scoreStrings = input.split(",");
            int arrowCount = 0;
            float scoreSum = 0.0f;

            for (String scoreStr : scoreStrings) {
                scoreStr = scoreStr.trim();
                if (!scoreStr.isEmpty()) {
                    float score = Float.parseFloat(scoreStr);
                    if (score < 0 || score > 10) {
                        Toast.makeText(this, "Les scores doivent être entre 0 et 10", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    scoreSum += score;
                    arrowCount++;
                }
            }

            if (arrowCount == 0) {
                Toast.makeText(this, "Aucun score valide trouvé", Toast.LENGTH_SHORT).show();
                return;
            }

            // Mettre à jour les compteurs
            currentSum += arrowCount;
            totalScoreSum += scoreSum;
            totalScoreCount += arrowCount;
            lastAddedValue = arrowCount;

            updateUI();
            saveData();
            editTextScores.setText(""); // Effacer le champ après l'ajout
            textViewScorePreview.setText("");
            
            // Mettre à jour l'état du bouton annuler
            buttonUndo.setEnabled(lastAddedValue > 0);

            // Afficher un résumé
            float avgScore = scoreSum / arrowCount;
            Toast.makeText(this, String.format(Locale.getDefault(), 
                "%d flèches ajoutées (moyenne: %.1f)", arrowCount, avgScore), 
                Toast.LENGTH_SHORT).show();

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Format de scores invalide. Utilisez: 9,8,10,7", Toast.LENGTH_SHORT).show();
        }
    }

    private void undoLastAdd() {
        if (lastAddedValue > 0 && currentSum >= lastAddedValue) {
            currentSum -= lastAddedValue;
            Toast.makeText(this, "Dernier ajout de " + lastAddedValue + " flèches annulé", Toast.LENGTH_SHORT).show();
            lastAddedValue = 0;
            updateUI();
            saveData();
            buttonUndo.setEnabled(false);
        } else {
            Toast.makeText(this, "Rien à annuler", Toast.LENGTH_SHORT).show();
        }
    }

    private void openStatsActivity() {
        Intent intent = new Intent(this, StatsActivity.class);
        startActivity(intent);
    }

    private void exportDataToCsv() {
        try {
            // S'assurer que le répertoire existe
            File cacheDir = new File(getCacheDir(), "csv");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            // Créer un fichier avec un nom basé sur la date actuelle
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File csvFile = new File(cacheDir, "fleches_tirees_" + timestamp + ".csv");

            FileWriter writer = new FileWriter(csvFile);

            // Écrire l'en-tête du CSV
            writer.append("Date,Flèches tirées\n");

            // Créer une map avec toutes les données, incluant les jours avec 0 flèches
            TreeMap<String, Integer> allData = new TreeMap<>(arrowHistory);
            if (currentSum >= 0) {  // Inclure le jour actuel même s'il est à 0
                allData.put(currentDate, currentSum);
            }

            // Écrire les données
            for (Map.Entry<String, Integer> entry : allData.entrySet()) {
                writer.append(formatDateForDisplay(entry.getKey()))
                      .append(",")
                      .append(String.valueOf(entry.getValue()))
                      .append("\n");
            }

            writer.flush();
            writer.close();

            // Partager le fichier
            shareFile(csvFile);

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Erreur lors de l'exportation : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importDataFromCsv() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        try {
            csvFileLauncher.launch(Intent.createChooser(intent, "Sélectionner un fichier CSV"));
        } catch (Exception e) {
            Toast.makeText(this, "Erreur lors de l'ouverture du sélecteur de fichier", Toast.LENGTH_SHORT).show();
        }
    }

    private void processCsvFile(Uri csvUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(csvUri);
            if (inputStream == null) {
                Toast.makeText(this, "Impossible de lire le fichier", Toast.LENGTH_SHORT).show();
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            boolean isFirstLine = true;
            Map<String, Integer> importedData = new HashMap<>();
            int importedCount = 0;

            // Lire le fichier ligne par ligne
            while ((line = reader.readLine()) != null) {
                // Ignorer la première ligne (en-tête)
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                // Parser la ligne CSV
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    try {
                        String dateStr = parts[0].trim();
                        int arrowCount = Integer.parseInt(parts[1].trim());

                        // Convertir la date du format d'affichage vers le format interne
                        String internalDate = convertDisplayDateToInternal(dateStr);
                        if (internalDate != null) {
                            importedData.put(internalDate, arrowCount);
                            importedCount++;
                        }
                    } catch (NumberFormatException e) {
                        // Ignorer les lignes avec des erreurs de format
                        continue;
                    }
                }
            }

            reader.close();
            inputStream.close();

            if (importedCount > 0) {
                // Fusionner les données importées avec l'historique existant
                mergeImportedData(importedData);
                Toast.makeText(this, "Import réussi : " + importedCount + " entrées importées", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Aucune donnée valide trouvée dans le fichier", Toast.LENGTH_SHORT).show();
            }

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Erreur lors de la lecture du fichier : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String convertDisplayDateToInternal(String displayDate) {
        try {
            // Convertir de dd/MM/yyyy vers yyyy-MM-dd
            SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            SimpleDateFormat internalFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = displayFormat.parse(displayDate);
            return date != null ? internalFormat.format(date) : null;
        } catch (ParseException e) {
            // Essayer aussi le format anglais MM/dd/yyyy
            try {
                SimpleDateFormat usFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
                SimpleDateFormat internalFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date date = usFormat.parse(displayDate);
                return date != null ? internalFormat.format(date) : null;
            } catch (ParseException e2) {
                // Essayer le format interne directement
                try {
                    SimpleDateFormat internalFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    Date date = internalFormat.parse(displayDate);
                    return date != null ? displayDate : null;
                } catch (ParseException e3) {
                    return null;
                }
            }
        }
    }

    private void mergeImportedData(Map<String, Integer> importedData) {
        // Compter combien d'entrées existantes seraient écrasées
        int overwriteCount = 0;
        for (String date : importedData.keySet()) {
            if (arrowHistory.containsKey(date)) {
                overwriteCount++;
            }
        }

        String message = "Prêt à importer " + importedData.size() + " entrées.";
        if (overwriteCount > 0) {
            message += "\n\nAttention : " + overwriteCount + " entrées existantes seront remplacées.";
        }
        message += "\n\nVoulez-vous continuer ?";

        new AlertDialog.Builder(this)
                .setTitle("Confirmer l'import")
                .setMessage(message)
                .setPositiveButton("Importer", (dialog, which) -> {
                    // Procéder à l'import
                    performDataMerge(importedData);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void performDataMerge(Map<String, Integer> importedData) {
        // Trouver la plage de dates dans les données importées
        String minDate = null;
        String maxDate = null;
        
        for (String date : importedData.keySet()) {
            if (minDate == null || date.compareTo(minDate) < 0) {
                minDate = date;
            }
            if (maxDate == null || date.compareTo(maxDate) > 0) {
                maxDate = date;
            }
        }

        if (minDate != null && maxDate != null) {
            // Remplir tous les jours manquants avec 0 flèches dans la plage
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            try {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(sdf.parse(minDate));
                Date endDate = sdf.parse(maxDate);

                while (!calendar.getTime().after(endDate)) {
                    String dateString = sdf.format(calendar.getTime());
                    
                    // Si la date n'existe pas dans les données importées, ajouter 0
                    if (!importedData.containsKey(dateString)) {
                        importedData.put(dateString, 0);
                    }
                    
                    calendar.add(Calendar.DAY_OF_YEAR, 1);
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        // Fusionner avec l'historique existant
        int importedCount = 0;
        for (Map.Entry<String, Integer> entry : importedData.entrySet()) {
            arrowHistory.put(entry.getKey(), entry.getValue());
            importedCount++;
        }

        // Sauvegarder les données mises à jour
        saveData();
        
        // Mettre à jour l'interface utilisateur
        updateUI();
        
        Toast.makeText(this, "Import terminé : " + importedCount + " entrées fusionnées", Toast.LENGTH_LONG).show();
    }

    private void shareFile(File file) {
        Uri fileUri = FileProvider.getUriForFile(
                this,
                getApplicationContext().getPackageName() + ".provider",
                file);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/csv");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Données des flèches tirées");
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(shareIntent, "Partager via"));
    }

    private String formatDateForDisplay(String dateString) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date date = inputFormat.parse(dateString);
            return date != null ? outputFormat.format(date) : dateString;
        } catch (Exception e) {
            return dateString;
        }
    }

    private void openObjectivesActivity() {
        Intent intent = new Intent(this, ObjectivesActivity.class);
        startActivity(intent);
    }

    private void updateModeUI() {
        if (isScoringMode) {
            layoutCountingMode.setVisibility(View.GONE);
            layoutScoringMode.setVisibility(View.VISIBLE);
            layoutScoreInfo.setVisibility(View.VISIBLE);
            textViewModeInfo.setText("Saisissez vos scores");
        } else {
            layoutCountingMode.setVisibility(View.VISIBLE);
            layoutScoringMode.setVisibility(View.GONE);
            layoutScoreInfo.setVisibility(View.GONE);
            textViewModeInfo.setText("Comptez vos flèches");
        }
    }

    private void updateScorePreview() {
        String input = editTextScores.getText().toString();
        if (TextUtils.isEmpty(input)) {
            textViewScorePreview.setText("");
            return;
        }

        try {
            String[] scoreStrings = input.split(",");
            int arrowCount = 0;
            float scoreSum = 0.0f;
            StringBuilder validScores = new StringBuilder();

            for (String scoreStr : scoreStrings) {
                scoreStr = scoreStr.trim();
                if (!scoreStr.isEmpty()) {
                    float score = Float.parseFloat(scoreStr);
                    if (score >= 0 && score <= 10) {
                        scoreSum += score;
                        arrowCount++;
                        if (validScores.length() > 0) validScores.append(", ");
                        validScores.append(scoreStr);
                    }
                }
            }

            if (arrowCount > 0) {
                float average = scoreSum / arrowCount;
                textViewScorePreview.setText(String.format(Locale.getDefault(),
                    "Scores: %s\n%d flèches, moyenne: %.1f",
                    validScores.toString(), arrowCount, average));
            } else {
                textViewScorePreview.setText("Aucun score valide");
            }

        } catch (NumberFormatException e) {
            textViewScorePreview.setText("Format invalide");
        }
    }

    private void updateObjectiveDisplay() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean hasObjective = prefs.getBoolean(KEY_OBJECTIVE_ACTIVE, false);

        if (hasObjective) {
            int targetArrows = prefs.getInt(KEY_OBJECTIVE_TARGET, 0);
            String endDate = prefs.getString(KEY_OBJECTIVE_END_DATE, "");
            
            // Calculer le progrès depuis le début de l'objectif
            String startDate = prefs.getString(KEY_OBJECTIVE_START_DATE, "");
            int progress = calculateObjectiveProgress(startDate);
            
            textViewObjectiveProgress.setText(String.format("%d/%d", progress, targetArrows));
            
            // Calculer l'objectif quotidien
            String dailyGoalMessage = calculateDailyGoalMessage(targetArrows, progress, endDate);
            textViewDailyGoal.setText(dailyGoalMessage);
            
            layoutObjective.setVisibility(View.VISIBLE);
            
            // Changer la couleur selon le progrès
            if (progress >= targetArrows) {
                textViewObjectiveProgress.setTextColor(0xFF4CAF50); // Vert
                textViewDailyGoal.setText("🎯 Objectif atteint ! Félicitations !");
                textViewDailyGoal.setTextColor(0xFF4CAF50);
            } else {
                textViewObjectiveProgress.setTextColor(0xFF1976D2); // Bleu
                textViewDailyGoal.setTextColor(0xFF1565C0);
            }
        } else {
            layoutObjective.setVisibility(View.GONE);
        }
    }

    private String calculateDailyGoalMessage(int targetArrows, int currentProgress, String endDate) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date end = dateFormat.parse(endDate);
            Date now = new Date();
            
            long diffInMillies = end.getTime() - now.getTime();
            int daysRemaining = (int) (diffInMillies / (1000 * 60 * 60 * 24)) + 1; // +1 pour inclure aujourd'hui
            
            if (daysRemaining <= 0) {
                return "⏰ Objectif terminé";
            }
            
            int remainingArrows = targetArrows - currentProgress;
            if (remainingArrows <= 0) {
                return "🎯 Objectif déjà atteint !";
            }
            
            int dailyTarget = (int) Math.ceil((double) remainingArrows / daysRemaining);
            int alreadyToday = currentSum; // Flèches déjà tirées aujourd'hui
            int stillNeededToday = Math.max(0, dailyTarget - alreadyToday);
            
            if (stillNeededToday == 0) {
                return String.format("✅ Objectif du jour atteint ! (%d flèches recommandées)", dailyTarget);
            } else {
                return String.format("🏹 Il vous faut encore %d flèches aujourd'hui (objectif: %d/jour)", 
                    stillNeededToday, dailyTarget);
            }
            
        } catch (Exception e) {
            return "Erreur de calcul de l'objectif";
        }
    }

    private int calculateObjectiveProgress(String startDate) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, ?> allPrefs = prefs.getAll();
        
        int totalArrows = 0;
        String currentDate = getCurrentDateString();
        
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
        totalArrows += currentSum;
        
        return totalArrows;
    }

    private void updateUI() {
        textViewSum.setText(String.valueOf(currentSum));
        textViewLastSum.setText("Somme précédente : " + lastSum);
        
        // Mettre à jour le score moyen si en mode score
        if (isScoringMode && totalScoreCount > 0) {
            float averageScore = totalScoreSum / totalScoreCount;
            textViewAverageScore.setText(String.format(Locale.getDefault(), "%.1f", averageScore));
        } else {
            textViewAverageScore.setText("0.0");
        }
        
        updateDurationText();
        updateHistoryDisplay();
        updateObjectiveDisplay();
        
        // Mettre à jour l'état du bouton annuler
        buttonUndo.setEnabled(lastAddedValue > 0);
    }

    private void updateHistoryDisplay() {
        historyContainer.removeAllViews();
        
        // Afficher le titre de l'historique
        TextView titleView = new TextView(this);
        titleView.setText("Historique des flèches tirées :");
        titleView.setTextSize(18);
        titleView.setPadding(0, 16, 0, 8);
        historyContainer.addView(titleView);
        
        // Créer une map complète incluant le jour actuel
        TreeMap<String, Integer> completeHistory = new TreeMap<>(arrowHistory);
        completeHistory.put(currentDate, currentSum);
        
        // Afficher l'historique dans l'ordre chronologique inverse (plus récent en haut)
        for (Map.Entry<String, Integer> entry : completeHistory.descendingMap().entrySet()) {
            TextView entryView = new TextView(this);
            String dayLabel = entry.getKey().equals(currentDate) ? " (aujourd'hui)" : "";
            String arrowText = entry.getValue() == 1 ? " flèche" : " flèches";
            entryView.setText(formatDate(entry.getKey()) + dayLabel + " : " + entry.getValue() + arrowText);
            entryView.setTextSize(16);
            entryView.setPadding(16, 8, 0, 8);
            historyContainer.addView(entryView);
        }
        
        // Faire défiler vers le haut pour voir les entrées les plus récentes
        historyScrollView.post(new Runnable() {
            @Override
            public void run() {
                historyScrollView.fullScroll(ScrollView.FOCUS_UP);
            }
        });
    }
    
    private String formatDate(String dateString) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date date = inputFormat.parse(dateString);
            return date != null ? outputFormat.format(date) : dateString;
        } catch (Exception e) {
            return dateString;
        }
    }

    private void updateDurationText() {
        long elapsedMillis = SystemClock.elapsedRealtime() - lastResetTime;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis);
        long hours = TimeUnit.MILLISECONDS.toHours(elapsedMillis);

        String durationText;
        if (hours > 0) {
            durationText = String.format("Durée : %d h %d min %d s", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            durationText = String.format("Durée : %d min %d s", minutes, seconds % 60);
        } else {
            durationText = String.format("Durée : %d secondes", seconds);
        }

        textViewDuration.setText(durationText);
    }

    private void startDurationUpdates() {
        // Mettre à jour la durée toutes les secondes
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!isFinishing()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateDurationText();
                            // Vérifier périodiquement si nous sommes sur un nouveau jour
                            checkDailyReset();
                        }
                    });
                    try {
                        Thread.sleep(1000); // Attendre 1 seconde
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Sauvegarder le jour actuel dans l'historique même s'il est à 0
        arrowHistory.put(currentDate, currentSum);
        saveData(); // Sauvegarder les données lorsque l'application est mise en pause
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Vérifier si un jour s'est écoulé pendant que l'app était fermée
        checkDailyReset();
        updateUI();
        updateObjectiveDisplay(); // Mettre à jour l'affichage des objectifs
    }
}
