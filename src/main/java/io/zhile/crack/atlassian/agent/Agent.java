package io.zhile.crack.atlassian.agent;

import java.lang.instrument.Instrumentation;

/**
 * @author pengzhile
 * @link https://zhile.io
 * @version 1.0
 */
public class Agent {
    public static void premain(String args, Instrumentation inst) {
        final String appLibPath = args;
        if (appLibPath != null && !appLibPath.isEmpty()) {
            System.out.println("atlassian-agent: application lib path = " + appLibPath);
        }
        try {
            inst.addTransformer(new KeyTransformer(appLibPath));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
