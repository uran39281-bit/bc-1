package com.blackice.bc1;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class DiagnosticsTest {
    @Test public void boundedReportsRetainTechnicalEvidenceAndExcludePrivateMessages()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        CrashLogs.operation("Imported private-media-title.mp4");
        for(int i=0;i<14;i++)CrashLogs.record("Diagnostic acceptance probe",new IllegalArgumentException("PRIVATE_TOKEN /private/path/source.mp4"));
        long deadline=android.os.SystemClock.elapsedRealtime()+10000;String report="";while(android.os.SystemClock.elapsedRealtime()<deadline){report=CrashLogs.report(context);if(report.contains("Diagnostic acceptance probe")&&new File(context.getFilesDir(),"crash-logs").listFiles().length>=12)break;Thread.sleep(100);}
        assertTrue(report.contains("java.lang.IllegalArgumentException"));assertTrue(report.contains("DiagnosticsTest"));assertTrue(report.contains("Thread ID:"));assertFalse(report.contains("PRIVATE_TOKEN"));assertFalse(report.contains("private-media-title"));assertFalse(report.contains("/private/path"));
        File[] reports=new File(context.getFilesDir(),"crash-logs").listFiles();assertNotNull(reports);assertTrue(reports.length<=12);
        for(File f:reports)assertTrue("Report byte size bounded",f.length()<=128000);
        // Export uses exactly this UTF-8 report, independently of the dialog lifecycle.
        File exported=new File(context.getCacheDir(),"diagnostics-acceptance.txt");try(OutputStream out=new FileOutputStream(exported)){out.write(report.getBytes(StandardCharsets.UTF_8));}assertEquals(report,new String(java.nio.file.Files.readAllBytes(exported.toPath()),StandardCharsets.UTF_8));
        // The next-launch restoration path explicitly distinguishes a checkpoint from a confirmed crash.
        File session=new File(context.getFilesDir(),"diagnostic-session.txt");java.nio.file.Files.write(session.toPath(),"Session state: background\nRecent operation categories:\nEditing".getBytes(StandardCharsets.UTF_8));
        java.lang.reflect.Method restore=CrashLogs.class.getDeclaredMethod("restoreSession");restore.setAccessible(true);restore.invoke(context.getApplicationContext());
        assertTrue(CrashLogs.report(context).contains("not proof of a crash"));
    }
}
