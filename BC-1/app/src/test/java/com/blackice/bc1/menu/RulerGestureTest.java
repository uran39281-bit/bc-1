package com.blackice.bc1.menu;
import org.junit.Test;
import static org.junit.Assert.*;
public class RulerGestureTest {
 @Test public void phoneStripFollowsDragAcrossZero(){RulerGesture g=new RulerGesture(2,100);assertEquals(-3,g.move(150,-.1,-100,100),1e-9);assertEquals(4,g.move(80,-.1,-100,100),1e-9);}
 @Test public void wideRetainsIncreasingRightwardValue(){assertEquals(7,new RulerGesture(2,100).move(150,.1,-100,100),1e-9);}
 @Test public void reversalAtEitherBoundConsumesOverscroll(){RulerGesture g=new RulerGesture(50,0);assertEquals(0,g.move(1000,-1,0,100),0);assertEquals(1,g.move(999,-1,0,100),0);assertEquals(100,g.move(-1000,-1,0,100),0);assertEquals(99,g.move(-999,-1,0,100),0);}
 @Test public void largeScaleHasNoViewportLimit(){assertEquals(9900,new RulerGesture(100,0).move(-9800,-1,5,10000),0);}
}
