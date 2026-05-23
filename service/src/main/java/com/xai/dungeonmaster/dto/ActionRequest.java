package com.xai.dungeonmaster.dto;

/**
 * Request body for POST /api/game/action.
 *
 * The client sends the label of the choice they want to execute.
 * Example JSON:  { "choiceLabel": "Attack" }
 */
public class ActionRequest {

    private String choiceLabel;

    public ActionRequest() {}

    public ActionRequest(String choiceLabel) {
        this.choiceLabel = choiceLabel;
    }

    public String getChoiceLabel() {
        return choiceLabel;
    }

    public void setChoiceLabel(String choiceLabel) {
        this.choiceLabel = choiceLabel;
    }
}
