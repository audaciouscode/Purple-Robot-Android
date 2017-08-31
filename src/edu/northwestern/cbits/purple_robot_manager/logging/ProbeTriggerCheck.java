package edu.northwestern.cbits.purple_robot_manager.logging;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.triggers.ProbeTrigger;
import edu.northwestern.cbits.purple_robot_manager.triggers.Trigger;
import edu.northwestern.cbits.purple_robot_manager.triggers.TriggerManager;

public class ProbeTriggerCheck extends SanityCheck
{
    public String name(Context context)
    {
        return context.getString(R.string.name_sanity_probe_trigger_missing);
    }

    @SuppressWarnings("deprecation")
    public void runCheck(Context context)
    {
        this._errorLevel = SanityCheck.OK;

        synchronized (this)
        {
            List<Trigger> triggers = new ArrayList<>();
            triggers.addAll(TriggerManager.getInstance(context).allTriggers());

            for (Trigger trigger : triggers)
            {
                if (trigger instanceof ProbeTrigger)
                {
                    ProbeTrigger probeTrigger = (ProbeTrigger) trigger;

                    if (!probeTrigger.probeExists(context)) {
                        this._errorLevel = SanityCheck.WARNING;
                        this._errorMessage = context.getString(R.string.name_sanity_probe_trigger_missing_warning, probeTrigger.probeName(), probeTrigger.name());
                    }
                }
            }
        }
    }
}
