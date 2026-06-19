package test;

import campaign.CampaignManager;
import campaign.CampaignManager.ForcePlacement;
import campaign.CampaignManager.ScenarioDefinition;

public class ScenarioInspector {
    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : "/resources/scenarios/caesar/alexandria.csv";
        CampaignManager cm = new CampaignManager();
        ScenarioDefinition sd = cm.loadScenarioDefinition(path);
        if (sd == null) {
            System.out.println("Failed to load: " + path);
            return;
        }
        System.out.println("Scenario: " + sd.getName() + " -> " + sd.getHistoricalBattle());
        for (ForcePlacement fp : sd.getForces()) {
            System.out.printf("Force: culture=%s type=%s x=%d y=%d size=%d exp=%d%n", fp.getCulture(), fp.getType(),
                    fp.getX(), fp.getY(), fp.getSize(), fp.getExperienceLevel());
        }
    }
}
