package edu.northwestern.cbits.purple_robot_manager.util;

import android.os.Bundle;

import java.util.Set;

public class Bundles {
    public static boolean areEqual(Bundle one, Bundle two) {
        if(one.size() != two.size()) {
            return false;
        }

        Set<String> setOne = one.keySet();
        Object valueOne;
        Object valueTwo;

        for(String key : setOne) {
            valueOne = one.get(key);
            valueTwo = two.get(key);
            if(valueOne instanceof Bundle && valueTwo instanceof Bundle &&
                    !Bundles.areEqual((Bundle) valueOne, (Bundle) valueTwo)) {
                return false;
            }
            else if(valueOne == null) {
                if(valueTwo != null || !two.containsKey(key)) {
                    return false;
                }
            }
            else if(!valueOne.equals(valueTwo)) {
                return false;
            }
        }

        return true;
    }
}
