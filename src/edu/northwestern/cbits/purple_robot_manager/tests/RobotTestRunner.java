package edu.northwestern.cbits.purple_robot_manager.tests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;
import android.content.Context;
import android.test.AndroidTestRunner;
import edu.northwestern.cbits.purple_robot_manager.tests.models.MatlabForestModelTestCase;
import edu.northwestern.cbits.purple_robot_manager.tests.models.MatlabTreeModelTestCase;
import edu.northwestern.cbits.purple_robot_manager.tests.models.WekaTreeModelTestCase;
import edu.northwestern.cbits.purple_robot_manager.tests.ui.NonAsciiDialogTestCase;
import edu.northwestern.cbits.purple_robot_manager.tests.ui.NotificationTestCase;

public class RobotTestRunner extends AndroidTestRunner
{
    private final List<TestCase> _cases = new ArrayList<>();
    private final RobotTestSuite _suite = new RobotTestSuite();

    private Thread _thread = null;

    public RobotTestRunner(Context context)
    {
        super();

        this._suite.addTest(new JUnitTestCase(context, 0));
        this._suite.addTest(new EncryptionTestCase(context, 0));
        this._suite.addTest(new DateTriggerTestCase(context, 0));
        this._suite.addTest(new SnapshotsTestCase(context, 0));
        this._suite.addTest(new UserIdTestCase(context, 3));
        this._suite.addTest(new LocalHttpEndpointTestCase(context, 3));
        this._suite.addTest(new NonAsciiDialogTestCase(context, 5));
        this._suite.addTest(new NotificationTestCase(context, 5));
        this._suite.addTest(new JavascriptTestCase(context, 6));
        this._suite.addTest(new JavascriptProbeSettingsTest(context, 6));
        this._suite.addTest(new LocalLogServerTestCase(context, 8));
        this._suite.addTest(new AccelerometerProbeTestCase(context, 8));
        this._suite.addTest(new WekaTreeModelTestCase(context, 8));
        this._suite.addTest(new MatlabTreeModelTestCase(context, 8));
        this._suite.addTest(new MatlabForestModelTestCase(context, 8));
        this._suite.addTest(new PurpleRobotHealthProbeTestCase(context, 8));
        this._suite.addTest(new HalfHourDateTriggerTestCase(context, 9));
        this._suite.addTest(new RandomDateTriggerTestCase(context, 10));
    }

    public List<TestCase> getTestCases(final Context context)
    {
        if (this._cases.size() == 0)
        {
            Enumeration<Test> tests = this._suite.tests();

            while (tests.hasMoreElements())
            {
                Test test = tests.nextElement();

                if (test instanceof RobotTestCase)
                {
                    RobotTestCase robotTest = (RobotTestCase) test;

                    this._cases.add(robotTest);
                }
            }
        }

        Collections.sort(this._cases, new Comparator<TestCase>()
        {
            @Override
            public int compare(TestCase one, TestCase two)
            {
                if (one instanceof RobotTestCase && two instanceof RobotTestCase)
                {
                    RobotTestCase robotOne = (RobotTestCase) one;
                    RobotTestCase robotTwo = (RobotTestCase) two;

                    return robotOne.compareTo(context, robotTwo);
                }
                else if (one instanceof RobotTestCase)
                    return -1;
                else if (two instanceof RobotTestCase)
                    return 1;

                return one.getName().compareTo(two.getName());
            }
        });

        return this._cases;
    }

    public boolean isRunning()
    {
        return this._thread != null;
    }

    public void startTests(final Context context, final TestResult result, final Runnable next)
    {
        final RobotTestRunner me = this;

        this._thread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                for (TestCase testCase : me.getTestCases(context))
                {
                    try {
                        testCase.run(result);
                    }
                    catch (Throwable e)
                    {
                        e.printStackTrace();

                        throw e;
                    }
                }

                me._thread = null;

                if (next != null)
                    next.run();
            }
        });

        this._thread.start();
    }

    public void stopTests()
    {
        if (this._thread != null)
        {
            this._thread.interrupt();
        }
    }
}
