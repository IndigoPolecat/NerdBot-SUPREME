package net.hypixel.nerdbot.api.curator;

import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.JDA;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;

import java.util.List;

@Getter
@Setter
public abstract class Curator<T> {

    private long startTime;
    private long endTime;
    private final boolean readOnly;

    protected Curator(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public abstract List<GreenlitMessage> curate(T t);

    public double getRatio(double positiveReactions, double negativeReactions) {
        if (positiveReactions == 0 && negativeReactions == 0) {
            return 0;
        }
        return positiveReactions / (positiveReactions + negativeReactions) * 100;
    }

    public JDA getJDA() {
        return NerdBotApp.getBot().getJDA();
    }
}
