package com.app.common.weka;

import com.app.common.model.MatchFeatures;
import com.app.common.util.PredictionUtils;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared Weka schema definition used by both the main app and the model-training-service.
 *
 * <p><strong>IMPORTANT:</strong> This is the single source of truth for the Weka dataset schema.
 * Both {@code football-prediction-app} and {@code model-training-service} MUST use this class
 * to build attributes and convert MatchFeatures to Weka Instances. This prevents schema drift
 * between training and serving.</p>
 *
 * <p>Column indices must match {@link #buildAttributes()} order exactly.</p>
 */
public final class WekaSchemaBuilder {

    private WekaSchemaBuilder() {
        // Utility class
    }

    // ── Weka column indices (must match buildAttributes() order exactly) ──
    public static final int IDX_HOME_FORM         = 0;
    public static final int IDX_AWAY_FORM         = 1;
    public static final int IDX_HOME_GOALS_SCR    = 2;
    public static final int IDX_HOME_GOALS_CON    = 3;
    public static final int IDX_AWAY_GOALS_SCR    = 4;
    public static final int IDX_AWAY_GOALS_CON    = 5;
    public static final int IDX_HOME_TOTAL_GOALS  = 6;
    public static final int IDX_AWAY_TOTAL_GOALS  = 7;
    public static final int IDX_H2H_HOME_WIN      = 8;
    public static final int IDX_H2H_DRAW          = 9;
    public static final int IDX_H2H_AWAY_WIN      = 10;
    public static final int IDX_HOME_SHOTS        = 11;
    public static final int IDX_AWAY_SHOTS        = 12;
    public static final int IDX_HOME_CORNERS      = 13;
    public static final int IDX_AWAY_CORNERS      = 14;
    // Phase 3 features
    public static final int IDX_HOME_GOAL_DIFF    = 15;
    public static final int IDX_AWAY_GOAL_DIFF    = 16;
    public static final int IDX_HOME_OVERALL_FORM = 17;
    public static final int IDX_AWAY_OVERALL_FORM = 18;
    public static final int IDX_HOME_WIN_STREAK   = 19;
    public static final int IDX_AWAY_WIN_STREAK   = 20;
    public static final int IDX_HOME_UNBEATEN     = 21;
    public static final int IDX_AWAY_UNBEATEN     = 22;
    public static final int IDX_HOME_DAYS_REST    = 23;
    public static final int IDX_AWAY_DAYS_REST    = 24;
    // Phase 5 features (Possession Proxy)
    public static final int IDX_HOME_POSSESSION   = 25;
    public static final int IDX_AWAY_POSSESSION   = 26;
    // Phase 4 features (Half-Time & League Position)
    public static final int IDX_HOME_HT_LEAD_RATE = 27;
    public static final int IDX_AWAY_HT_LEAD_RATE = 28;
    public static final int IDX_HOME_COMEBACK     = 29;
    public static final int IDX_AWAY_COMEBACK     = 30;
    public static final int IDX_HOME_LEAGUE_POS   = 31;
    public static final int IDX_AWAY_LEAGUE_POS   = 32;
    // Phase 6 features (Elo Ratings)
    public static final int IDX_HOME_ELO          = 33;
    public static final int IDX_AWAY_ELO          = 34;
    // Phase 7 features (Derived Interaction Features)
    public static final int IDX_FORM_DIFF         = 35;
    public static final int IDX_GOAL_DIFF_DIFF    = 36;
    public static final int IDX_H2H_DOMINANCE     = 37;
    public static final int IDX_REST_ADVANTAGE    = 38;
    public static final int IDX_ELO_DIFF          = 39;
    // Phase 8 features (Recency-Weighted Form)
    public static final int IDX_HOME_WEIGHTED_FORM = 40;
    public static final int IDX_AWAY_WEIGHTED_FORM = 41;
    // Phase 9 features (Motivation Level)
    public static final int IDX_HOME_MOTIVATION    = 42;
    public static final int IDX_AWAY_MOTIVATION    = 43;
    // Phase 10 features (Squad Strength)
    public static final int IDX_HOME_SQUAD_STRENGTH     = 44;
    public static final int IDX_AWAY_SQUAD_STRENGTH     = 45;
    public static final int IDX_SQUAD_STRENGTH_DIFF     = 46;
    // Phase 11 features (Draw-Specific Signals)
    public static final int IDX_FORM_SYMMETRY           = 47;
    public static final int IDX_GOAL_SYMMETRY           = 48;
    public static final int IDX_DRAW_TENDENCY           = 49;
    public static final int IDX_DEFENSIVE_TIGHTNESS     = 50;
    // Label
    public static final int IDX_LABEL             = 51;
    // Total number of attributes (features + label)
    public static final int TOTAL_ATTRIBUTES      = 52;
    // Number of input features (excluding label)
    public static final int FEATURE_COUNT         = 51;

    /**
     * Defines the schema of the Weka dataset.
     * ORDER MATTERS — indices must match the IDX_ constants above exactly.
     *
     * @return ArrayList of Weka Attributes
     */
    public static ArrayList<Attribute> buildAttributes() {
        ArrayList<Attribute> attrs = new ArrayList<>();

        // Phase 1 & 2 numeric features (indices 0–14)
        attrs.add(new Attribute("homeFormPoints"));        // 0
        attrs.add(new Attribute("awayFormPoints"));        // 1
        attrs.add(new Attribute("homeGoalsScoredAvg"));    // 2
        attrs.add(new Attribute("homeGoalsConcededAvg"));  // 3
        attrs.add(new Attribute("awayGoalsScoredAvg"));    // 4
        attrs.add(new Attribute("awayGoalsConcededAvg"));  // 5
        attrs.add(new Attribute("homeTotalGoalsAvg"));     // 6
        attrs.add(new Attribute("awayTotalGoalsAvg"));     // 7
        attrs.add(new Attribute("h2hHomeWinRate"));        // 8
        attrs.add(new Attribute("h2hDrawRate"));           // 9
        attrs.add(new Attribute("h2hAwayWinRate"));        // 10
        attrs.add(new Attribute("homeShotsOnTargetAvg"));  // 11
        attrs.add(new Attribute("awayShotsOnTargetAvg"));  // 12
        attrs.add(new Attribute("homeCornersAvg"));        // 13
        attrs.add(new Attribute("awayCornersAvg"));        // 14

        // Phase 3 numeric features (indices 15–24)
        attrs.add(new Attribute("homeGoalDifference"));    // 15
        attrs.add(new Attribute("awayGoalDifference"));    // 16
        attrs.add(new Attribute("homeOverallFormPoints")); // 17
        attrs.add(new Attribute("awayOverallFormPoints")); // 18
        attrs.add(new Attribute("homeWinStreak"));         // 19
        attrs.add(new Attribute("awayWinStreak"));         // 20
        attrs.add(new Attribute("homeUnbeatenStreak"));    // 21
        attrs.add(new Attribute("awayUnbeatenStreak"));    // 22
        attrs.add(new Attribute("homeDaysRest"));          // 23
        attrs.add(new Attribute("awayDaysRest"));          // 24

        // Phase 5 numeric features - Possession Proxy (indices 25-26)
        attrs.add(new Attribute("homePossessionProxy"));   // 25
        attrs.add(new Attribute("awayPossessionProxy"));   // 26

        // Phase 4 numeric features - Half-Time & League Position (indices 27-32)
        attrs.add(new Attribute("homeHalfTimeLeadRate"));  // 27
        attrs.add(new Attribute("awayHalfTimeLeadRate"));  // 28
        attrs.add(new Attribute("homeComebackRate"));      // 29
        attrs.add(new Attribute("awayComebackRate"));      // 30
        attrs.add(new Attribute("homeLeaguePosition"));    // 31
        attrs.add(new Attribute("awayLeaguePosition"));    // 32

        // Phase 6 numeric features - Elo Ratings (indices 33-34)
        attrs.add(new Attribute("homeEloRating"));         // 33
        attrs.add(new Attribute("awayEloRating"));         // 34

        // Phase 7 numeric features - Derived Interaction (indices 35-39)
        attrs.add(new Attribute("formDifference"));        // 35
        attrs.add(new Attribute("goalDiffDifference"));    // 36
        attrs.add(new Attribute("h2hDominance"));          // 37
        attrs.add(new Attribute("restAdvantage"));         // 38
        attrs.add(new Attribute("eloDifference"));         // 39

        // Phase 8 numeric features - Recency-Weighted Form (indices 40-41)
        attrs.add(new Attribute("homeWeightedForm"));      // 40
        attrs.add(new Attribute("awayWeightedForm"));      // 41

        // Phase 9 numeric features - Motivation Level (indices 42-43)
        attrs.add(new Attribute("homeMotivationLevel"));   // 42
        attrs.add(new Attribute("awayMotivationLevel"));   // 43

        // Phase 10 numeric features - Squad Strength (indices 44-46)
        attrs.add(new Attribute("homeSquadStrength"));     // 44
        attrs.add(new Attribute("awaySquadStrength"));     // 45
        attrs.add(new Attribute("squadStrengthDifference")); // 46

        // Phase 11 numeric features - Draw-Specific Signals (indices 47-50)
        attrs.add(new Attribute("formSymmetry"));          // 47
        attrs.add(new Attribute("goalSymmetry"));          // 48
        attrs.add(new Attribute("drawTendency"));          // 49
        attrs.add(new Attribute("defensiveTightness"));    // 50

        // Nominal label (index 51)
        ArrayList<String> labels = new ArrayList<>(List.of("H", "D", "A"));
        attrs.add(new Attribute("result", labels));        // 51

        return attrs;
    }

    /**
     * Converts a list of MatchFeatures into a Weka Instances dataset.
     *
     * @param featuresList List of match features
     * @param name Dataset name
     * @return Weka Instances with class index set
     */
    public static Instances toWekaInstances(List<MatchFeatures> featuresList, String name) {
        ArrayList<Attribute> attributes = buildAttributes();
        Instances dataset = new Instances(name, attributes, featuresList.size());
        dataset.setClassIndex(IDX_LABEL);

        for (MatchFeatures f : featuresList) {
            Instance inst = toWekaInstance(f, dataset);
            dataset.add(inst);
        }

        return dataset;
    }

    /**
     * Converts a single MatchFeatures into a Weka Instance.
     * Uses PredictionUtils.safe() to guard against NaN/Infinity from edge cases.
     *
     * @param f MatchFeatures to convert
     * @param dataset The dataset this instance belongs to (for schema reference)
     * @return Weka Instance
     */
    public static Instance toWekaInstance(MatchFeatures f, Instances dataset) {
        Instance inst = new DenseInstance(TOTAL_ATTRIBUTES);
        inst.setDataset(dataset);

        // Phase 1 & 2 features
        inst.setValue(IDX_HOME_FORM,        PredictionUtils.safe(f.getHomeFormPoints()));
        inst.setValue(IDX_AWAY_FORM,        PredictionUtils.safe(f.getAwayFormPoints()));
        inst.setValue(IDX_HOME_GOALS_SCR,   PredictionUtils.safe(f.getHomeGoalsScoredAvg()));
        inst.setValue(IDX_HOME_GOALS_CON,   PredictionUtils.safe(f.getHomeGoalsConcededAvg()));
        inst.setValue(IDX_AWAY_GOALS_SCR,   PredictionUtils.safe(f.getAwayGoalsScoredAvg()));
        inst.setValue(IDX_AWAY_GOALS_CON,   PredictionUtils.safe(f.getAwayGoalsConcededAvg()));
        inst.setValue(IDX_HOME_TOTAL_GOALS, PredictionUtils.safe(f.getHomeTotalGoalsAvg()));
        inst.setValue(IDX_AWAY_TOTAL_GOALS, PredictionUtils.safe(f.getAwayTotalGoalsAvg()));
        inst.setValue(IDX_H2H_HOME_WIN,     PredictionUtils.safe(f.getH2hHomeWinRate()));
        inst.setValue(IDX_H2H_DRAW,         PredictionUtils.safe(f.getH2hDrawRate()));
        inst.setValue(IDX_H2H_AWAY_WIN,     PredictionUtils.safe(f.getH2hAwayWinRate()));
        inst.setValue(IDX_HOME_SHOTS,       PredictionUtils.safe(f.getHomeShotsOnTargetAvg()));
        inst.setValue(IDX_AWAY_SHOTS,       PredictionUtils.safe(f.getAwayShotsOnTargetAvg()));
        inst.setValue(IDX_HOME_CORNERS,     PredictionUtils.safe(f.getHomeCornersAvg()));
        inst.setValue(IDX_AWAY_CORNERS,     PredictionUtils.safe(f.getAwayCornersAvg()));

        // Phase 3 features
        inst.setValue(IDX_HOME_GOAL_DIFF,    PredictionUtils.safe(f.getHomeGoalDifference()));
        inst.setValue(IDX_AWAY_GOAL_DIFF,    PredictionUtils.safe(f.getAwayGoalDifference()));
        inst.setValue(IDX_HOME_OVERALL_FORM, PredictionUtils.safe(f.getHomeOverallFormPoints()));
        inst.setValue(IDX_AWAY_OVERALL_FORM, PredictionUtils.safe(f.getAwayOverallFormPoints()));
        inst.setValue(IDX_HOME_WIN_STREAK,   PredictionUtils.safe(f.getHomeWinStreak()));
        inst.setValue(IDX_AWAY_WIN_STREAK,   PredictionUtils.safe(f.getAwayWinStreak()));
        inst.setValue(IDX_HOME_UNBEATEN,     PredictionUtils.safe(f.getHomeUnbeatenStreak()));
        inst.setValue(IDX_AWAY_UNBEATEN,     PredictionUtils.safe(f.getAwayUnbeatenStreak()));
        inst.setValue(IDX_HOME_DAYS_REST,    PredictionUtils.safe(f.getHomeDaysSinceLastMatch()));
        inst.setValue(IDX_AWAY_DAYS_REST,    PredictionUtils.safe(f.getAwayDaysSinceLastMatch()));

        // Phase 5 features (Possession Proxy)
        inst.setValue(IDX_HOME_POSSESSION,   PredictionUtils.safe(f.getHomePossessionProxy()));
        inst.setValue(IDX_AWAY_POSSESSION,   PredictionUtils.safe(f.getAwayPossessionProxy()));

        // Phase 4 features (Half-Time & League Position)
        inst.setValue(IDX_HOME_HT_LEAD_RATE, PredictionUtils.safe(f.getHomeHalfTimeLeadRate()));
        inst.setValue(IDX_AWAY_HT_LEAD_RATE, PredictionUtils.safe(f.getAwayHalfTimeLeadRate()));
        inst.setValue(IDX_HOME_COMEBACK,     PredictionUtils.safe(f.getHomeComebackRate()));
        inst.setValue(IDX_AWAY_COMEBACK,     PredictionUtils.safe(f.getAwayComebackRate()));
        inst.setValue(IDX_HOME_LEAGUE_POS,   PredictionUtils.safe(f.getHomeLeaguePosition()));
        inst.setValue(IDX_AWAY_LEAGUE_POS,   PredictionUtils.safe(f.getAwayLeaguePosition()));

        // Phase 6 features (Elo Ratings)
        inst.setValue(IDX_HOME_ELO,          PredictionUtils.safe(f.getHomeEloRating()));
        inst.setValue(IDX_AWAY_ELO,          PredictionUtils.safe(f.getAwayEloRating()));

        // Phase 7 features (Derived Interaction)
        inst.setValue(IDX_FORM_DIFF,         PredictionUtils.safe(f.getFormDifference()));
        inst.setValue(IDX_GOAL_DIFF_DIFF,    PredictionUtils.safe(f.getGoalDiffDifference()));
        inst.setValue(IDX_H2H_DOMINANCE,     PredictionUtils.safe(f.getH2hDominance()));
        inst.setValue(IDX_REST_ADVANTAGE,    PredictionUtils.safe(f.getRestAdvantage()));
        inst.setValue(IDX_ELO_DIFF,          PredictionUtils.safe(f.getEloDifference()));

        // Phase 8 features (Recency-Weighted Form)
        inst.setValue(IDX_HOME_WEIGHTED_FORM, PredictionUtils.safe(f.getHomeWeightedForm()));
        inst.setValue(IDX_AWAY_WEIGHTED_FORM, PredictionUtils.safe(f.getAwayWeightedForm()));

        // Phase 9 features (Motivation Level)
        inst.setValue(IDX_HOME_MOTIVATION,    PredictionUtils.safe(f.getHomeMotivationLevel()));
        inst.setValue(IDX_AWAY_MOTIVATION,    PredictionUtils.safe(f.getAwayMotivationLevel()));

        // Phase 10 features (Squad Strength)
        inst.setValue(IDX_HOME_SQUAD_STRENGTH,     PredictionUtils.safe(f.getHomeSquadStrength()));
        inst.setValue(IDX_AWAY_SQUAD_STRENGTH,     PredictionUtils.safe(f.getAwaySquadStrength()));
        inst.setValue(IDX_SQUAD_STRENGTH_DIFF,     PredictionUtils.safe(f.getSquadStrengthDifference()));

        // Phase 11 features (Draw-Specific Signals)
        inst.setValue(IDX_FORM_SYMMETRY,           PredictionUtils.safe(f.getFormSymmetry()));
        inst.setValue(IDX_GOAL_SYMMETRY,           PredictionUtils.safe(f.getGoalSymmetry()));
        inst.setValue(IDX_DRAW_TENDENCY,            PredictionUtils.safe(f.getDrawTendency()));
        inst.setValue(IDX_DEFENSIVE_TIGHTNESS,     PredictionUtils.safe(f.getDefensiveTightness()));

        // Label only set during training — null at prediction time
        if (f.getActualResult() != null) {
            inst.setValue(IDX_LABEL, f.getActualResult());
        }

        return inst;
    }
}

