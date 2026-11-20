package com.blackice.budget;

import android.app.*;
import android.content.*;
import java.time.*;
import org.json.*;

public final class Reminders extends BroadcastReceiver {
    static void schedule(Context c) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime next = now.toLocalDate().atTime(9,0).atZone(now.getZone());
        if (!next.isAfter(now)) next=next.plusDays(1);
        PendingIntent wake=PendingIntent.getBroadcast(c,0,new Intent(c,Reminders.class),PendingIntent.FLAG_IMMUTABLE);
        c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),wake);
    }
    @Override public void onReceive(Context c,Intent intent) {
        schedule(c);
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("renewals","Renewal reminders",NotificationManager.IMPORTANCE_DEFAULT));
        if(!nm.areNotificationsEnabled()) return;
        try {
            JSONObject data=new JSONObject(c.getSharedPreferences("budget",0).getString("data","{}"));
            JSONArray items=data.optJSONArray("items");
            if(items==null) return;
            JSONObject posted=data.getJSONObject("posted");
            for(int i=0;i<items.length();i++){
                JSONObject s=items.getJSONObject(i);
                int days=s.optInt("remind",-1);
                if(days<0 || !s.getString("kind").equals("Subscription")) continue;
                LocalDate due=LocalDate.now().plusDays(days), start=LocalDate.parse(s.getString("date"));
                if(due.isBefore(start)) continue;
                String cycle=s.getString("cycle");
                boolean match=start.equals(due);
                long elapsed=due.toEpochDay()-start.toEpochDay();
                if(cycle.equals("Weekly")) match=elapsed%7==0;
                if(cycle.equals("Biweekly")) match=elapsed%14==0;
                if(cycle.equals("Monthly")||cycle.equals("Yearly"))
                    match=due.getDayOfMonth()==Math.min(start.getDayOfMonth(),due.lengthOfMonth()) &&
                        (cycle.equals("Monthly") || due.getMonth()==start.getMonth());
                String key=s.getString("id")+"@"+due;
                if(!match || posted.has(key)) continue;
                PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,Main.class),PendingIntent.FLAG_IMMUTABLE);
                nm.notify(key,1,new Notification.Builder(c,"renewals").setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(s.getString("name")).setContentText("Renewal due "+due)
                    .setOnlyAlertOnce(true).setAutoCancel(true).setContentIntent(open).build());
            }
        } catch(JSONException ignored) { /* No saved ledger yet. */ }
    }
}
