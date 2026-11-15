package dev.mert.tvassistant;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE)
public class SearchAndroidTest {
  AccessibilityNodeInfo node(String pkg,String cls,String text,String description,boolean click,int l,int t,int r,int b){
    AccessibilityNodeInfo n=AccessibilityNodeInfo.obtain();n.setPackageName(pkg);n.setClassName("android.widget."+cls);n.setText(text);n.setContentDescription(description);n.setVisibleToUser(true);n.setClickable(click);n.setBoundsInScreen(new Rect(l,t,r,b));return n;
  }
  @Test public void zonaSearchWritesOnlyIntoTheObservedSearchToolbarAndGeneralPasswordTypingRemainsBlocked()throws Exception{
    NavigationService service=Robolectric.buildService(NavigationService.class).create().get();String p=MediaSearch.ZONA;
    AccessibilityNodeInfo root=node(p,"FrameLayout","","",false,0,0,1920,1080);
    AccessibilityNodeInfo field=node(p,"EditText","sensitive old value","",true,376,56,1680,152);field.setPassword(true);field.setEditable(true);
    Shadows.shadowOf(root).addChild(node(p,"Button","","Голосовой поиск",true,192,48,304,160));
    Shadows.shadowOf(root).addChild(field);Shadows.shadowOf(root).addChild(node(p,"TextView","Поиск","",false,376,71,540,137));
    Shadows.shadowOf(root).addChild(node(p,"Button","","Поиск",true,1728,48,1840,160));
    Shadows.shadowOf(service).setRootInActiveWindow(root);List<String> writes=new ArrayList<>();
    Shadows.shadowOf(field).setOnPerformActionListener((action,args)->{
      if(action==AccessibilityNodeInfo.ACTION_SET_TEXT){writes.add(args.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE).toString());return true;}return false;
    });
    try{
      JSONObject screen=service.inspect(),form=SearchUi.zonaForm(screen);assertNotNull(form);assertFalse(screen.toString().contains("sensitive old value"));
      try{service.type(screen.getString("snapshot"),form.getInt("field"),"Шерлок");fail("General password typing was accepted");}catch(IllegalArgumentException expected){}
      assertTrue(writes.isEmpty());assertTrue(service.typeZonaSearch("Шерлок"));assertEquals(Arrays.asList("Шерлок"),writes);
      root.setPackageName("other.app");try{service.typeZonaSearch("Не вводить");fail("Wrong app accepted");}catch(IllegalArgumentException expected){}
      assertEquals(1,writes.size());
    }finally{service.onDestroy();}
  }
}
