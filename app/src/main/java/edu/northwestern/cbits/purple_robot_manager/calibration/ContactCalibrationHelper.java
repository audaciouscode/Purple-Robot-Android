package edu.northwestern.cbits.purple_robot_manager.calibration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneNumberUtils;

import edu.emory.mathcs.backport.java.util.Collections;
import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.activities.probes.AddressBookLabelActivity;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.purple_robot_manager.logging.SanityCheck;
import edu.northwestern.cbits.purple_robot_manager.logging.SanityManager;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.CommunicationEventProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.CommunicationLogProbe;

public class ContactCalibrationHelper
{
    private static Map<String, String> _cache = new HashMap<>();
    private static SharedPreferences _cachedPrefs = null;

    public static void check(final Context context)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());

        final String title = context.getString(R.string.title_address_book_label_check);

        final SanityManager sanity = SanityManager.getInstance(context);

        int contactsCount = ContactCalibrationHelper.fetchContactRecords(context).size();

        if (contactsCount == 0)
        {
            sanity.clearAlert(title);

            return;
        }

        boolean logEnabled = prefs.getBoolean(CommunicationLogProbe.ENABLED, CommunicationLogProbe.DEFAULT_ENABLED);
        boolean logCalibrate = prefs.getBoolean(CommunicationLogProbe.ENABLE_CALIBRATION_NOTIFICATIONS, CommunicationLogProbe.DEFAULT_ENABLE_CALIBRATION_NOTIFICATIONS);

        boolean eventEnabled = prefs.getBoolean(CommunicationEventProbe.ENABLED, CommunicationEventProbe.DEFAULT_ENABLED);
        boolean eventCalibrate = prefs.getBoolean(CommunicationEventProbe.ENABLE_CALIBRATION_NOTIFICATIONS, CommunicationEventProbe.DEFAULT_ENABLE_CALIBRATION_NOTIFICATIONS);

        if ((logEnabled == false || logCalibrate == false) && (eventEnabled == false || eventCalibrate == false))
        {
            sanity.clearAlert(title);
        }
        else if (prefs.contains("last_address_book_calibration") == false)
        {
            String message = context.getString(R.string.message_address_book_label_check);
            Runnable action = new Runnable()
            {
                public void run()
                {
                    Intent intent = new Intent(context, AddressBookLabelActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    context.startActivity(intent);
                }
            };

            sanity.addAlert(SanityCheck.WARNING, title, message, action);
        }
    }

    @SuppressWarnings("deprecation")
    public static String getGroup(Context context, String key, boolean isPhone)
    {
        if (key == null)
            return null;

        if (ContactCalibrationHelper._cachedPrefs == null)
            ContactCalibrationHelper._cachedPrefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());

        String group = ContactCalibrationHelper._cachedPrefs.getString("contact_calibration_" + key + "_group", null);

        if (group != null)
            return group;

        if (isPhone)
        {
            String newKey = ContactCalibrationHelper._cache.get(key);

            if (newKey == null)
            {
                String numbersOnly = key.replaceAll("[^\\d]", "");

                if (numbersOnly.length() == 10)
                    numbersOnly = "1" + numbersOnly;
                else if (numbersOnly.length() == 11)
                    numbersOnly = numbersOnly.substring(1);

                newKey = PhoneNumberUtils.formatNumber(numbersOnly);

                ContactCalibrationHelper._cache.put(key, newKey);
            }

            key = newKey;
        }

        return ContactCalibrationHelper._cachedPrefs.getString("contact_calibration_" + key + "_group", null);
    }

    public static void setGroup(Context context, String key, String group)
    {
        if (ContactCalibrationHelper._cachedPrefs == null)
            ContactCalibrationHelper._cachedPrefs = PreferenceManager.getDefaultSharedPreferences(context
                    .getApplicationContext());

        Editor e = ContactCalibrationHelper._cachedPrefs.edit();
        e.putString("contact_calibration_" + key + "_group", group);
        e.apply();
    }

    @SuppressWarnings("deprecation")
    public static List<ContactRecord> fetchContactRecords(Context context)
    {
        ArrayList<ContactRecord> contacts = new ArrayList<>();
        ArrayList<ContactRecord> normalizedContacts = new ArrayList<>();

        HashMap<String, String> nameCache = new HashMap<>();

        try
        {
            boolean ready = true;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(context, "android.permission.READ_CALL_LOG") != PackageManager.PERMISSION_GRANTED) {
                    SanityManager.getInstance(context).addPermissionAlert("Contact Calibration Helper", "android.permission.READ_CALL_LOG", context.getString(R.string.rationale_calibration_call_log), null);
                    ready = false;
                }

                if (ContextCompat.checkSelfPermission(context, "android.permission.READ_CONTACTS") != PackageManager.PERMISSION_GRANTED) {
                    SanityManager.getInstance(context).addPermissionAlert("Contact Calibration Helper", "android.permission.READ_CONTACTS", context.getString(R.string.rationale_calibration_contacts), null);
                    ready = false;
                }
            }

            if (ready)
            {
                SanityManager.getInstance(context).clearPermissionAlert("android.permission.READ_CALL_LOG");
                SanityManager.getInstance(context).clearPermissionAlert("android.permission.READ_CONTACTS");

                Cursor c = context.getContentResolver().query(CallLog.Calls.CONTENT_URI, null, null, null, null);

                while (c.moveToNext()) {
                    String numberName = c.getString(c.getColumnIndex(Calls.CACHED_NAME));
                    String phoneNumber = PhoneNumberUtils.formatNumber(c.getString(c.getColumnIndex(Calls.NUMBER)));

                    boolean found = false;

                    if (numberName == null)
                        numberName = "";

                    for (ContactRecord contact : contacts) {
                        if (contact.number.endsWith(phoneNumber) || phoneNumber.endsWith(contact.number)) {
                            String largerNumber = contact.number;

                            if (phoneNumber.length() > largerNumber.length())
                                largerNumber = phoneNumber;

                            contact.number = largerNumber;

                            found = true;
                            contact.count += 1;

                            if ("".equals(numberName) == false && "".equals(contact.name))
                                contact.name = numberName;
                        }
                    }

                    if (found == false) {
                        ContactRecord contact = new ContactRecord();
                        contact.name = numberName;
                        contact.number = phoneNumber;

                        String key = contact.name;

                        boolean isPhone = false;

                        if ("".equals(key)) {
                            key = contact.number;
                            isPhone = true;
                        }

                        String group = ContactCalibrationHelper.getGroup(context, key, isPhone);

                        if (group != null)
                            contact.group = group;

                        contacts.add(contact);
                    }
                }

                c.close();

                c = context.getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, "date");

                while (c != null && c.moveToNext()) {
                    String numberName = c.getString(c.getColumnIndex("person"));
                    String phoneNumber = PhoneNumberUtils.formatNumber(c.getString(c.getColumnIndex("address")));

                    if (numberName == null)
                        numberName = phoneNumber;

                    boolean found = false;

                    for (ContactRecord contact : contacts) {
                        if (contact.number.endsWith(phoneNumber) || phoneNumber.endsWith(contact.number)) {
                            String largerNumber = contact.number;

                            if (phoneNumber.length() > largerNumber.length())
                                largerNumber = phoneNumber;

                            contact.number = largerNumber;

                            found = true;
                            contact.count += 1;

                            if ("".equals(numberName) == false && "".equals(contact.name))
                                contact.name = numberName;
                        }
                    }

                    if (nameCache.containsKey(phoneNumber) == false) {
                        nameCache.put(phoneNumber, "");

                        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
                        Cursor contactsCursor = context.getContentResolver().query(uri, null, null, null, null);

                        while (contactsCursor.moveToNext()) {
                            nameCache.put(phoneNumber, contactsCursor.getString(contactsCursor.getColumnIndex(ContactsContract.CommonDataKinds.Identity.DISPLAY_NAME)));
                        }

                        contactsCursor.close();
                    }

                    if (found == false && nameCache.get(phoneNumber).length() > 0) {
                        ContactRecord contact = new ContactRecord();
                        contact.name = nameCache.get(phoneNumber);
                        contact.number = phoneNumber;

                        contacts.add(contact);
                    }
                }

                if (c != null)
                    c.close();

                c = context.getContentResolver().query(Uri.parse("content://sms/sent"), null, null, null, "date");

                while (c != null && c.moveToNext()) {
                    String numberName = c.getString(c.getColumnIndex("person"));
                    String phoneNumber = PhoneNumberUtils.formatNumber(c.getString(c.getColumnIndex("address")));

                    if (numberName == null)
                        numberName = phoneNumber;

                    boolean found = false;

                    for (ContactRecord contact : contacts) {
                        if (contact.number.endsWith(phoneNumber) || phoneNumber.endsWith(contact.number)) {
                            String largerNumber = contact.number;

                            if (phoneNumber.length() > largerNumber.length())
                                largerNumber = phoneNumber;

                            contact.number = largerNumber;

                            found = true;
                            contact.count += 1;

                            if ("".equals(numberName) == false && "".equals(contact.name))
                                contact.name = numberName;
                        }
                    }


                    if (nameCache.containsKey(phoneNumber) == false) {
                        nameCache.put(phoneNumber, "");

                        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
                        Cursor contactsCursor = context.getContentResolver().query(uri, null, null, null, null);

                        while (contactsCursor.moveToNext()) {
                            nameCache.put(phoneNumber, contactsCursor.getString(contactsCursor.getColumnIndex(ContactsContract.CommonDataKinds.Identity.DISPLAY_NAME)));
                        }

                        contactsCursor.close();
                    }

                    if (found == false && nameCache.get(phoneNumber).length() > 0) {
                        ContactRecord contact = new ContactRecord();
                        contact.name = nameCache.get(phoneNumber);
                        contact.number = phoneNumber;

                        contacts.add(contact);
                    }
                }

                if (c != null)
                    c.close();

                Collections.sort(contacts);

                for (ContactRecord contact : contacts) {
                    if ("".equals(contact.name) == false) {
                        boolean found = false;

                        for (ContactRecord normalized : normalizedContacts) {
                            if (contact.name.equals(normalized.name)) {
                                found = true;

                                normalized.count += contact.count;
                            }
                        }

                        if (found == false)
                            normalizedContacts.add(contact);
                    } else
                        normalizedContacts.add(contact);
                }

                Collections.sort(normalizedContacts);
            }
        }
        catch (RuntimeException e)
        {
            LogManager.getInstance(context).logException(e);
        }

        return normalizedContacts;
    }

    public static void clear(Context context)
    {
        final SanityManager sanity = SanityManager.getInstance(context);

        sanity.clearAlert(context.getString(R.string.title_address_book_label_check));
    }
}
