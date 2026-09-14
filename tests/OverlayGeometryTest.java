package dev.glass.browser;
import java.util.Random;
public class OverlayGeometryTest {
    public static void main(String[] args) {
        Random rng=new Random(17);
        for(int i=0;i<10000;i++) {
            int sw=1+rng.nextInt(2400),sh=1+rng.nextInt(2400);
            int[] r=OverlayGeometry.fit(rng.nextInt(6000)-3000,rng.nextInt(6000)-3000,rng.nextInt(6000)-3000,rng.nextInt(6000)-3000,sw,sh,260,320);
            if(r[0]<0||r[1]<0||r[2]<Math.min(260,sw)||r[3]<Math.min(320,sh)||r[0]+r[2]>sw||r[1]+r[3]>sh)throw new AssertionError("Unreachable window at case "+i);
        }
        int[] portrait=OverlayGeometry.fit(50,700,350,500,400,900,260,320);
        int[] landscape=OverlayGeometry.fit(portrait[0],portrait[1],portrait[2],portrait[3],900,400,260,320);
        if(landscape[1]+landscape[3]>400)throw new AssertionError("Rotation hides controls");
        for(int corner=2;corner<=5;corner++)for(int i=0;i<3000;i++) {
            int[] b=OverlayGeometry.resize(corner,100,100,400,600,rng.nextInt(3000)-1500,rng.nextInt(3000)-1500,1000,1200,280,340);
            if(b[0]<0||b[1]<0||b[2]<280||b[3]<340||b[0]+b[2]>1000||b[1]+b[3]>1200)throw new AssertionError("Resize bounds");
            if((corner==2||corner==4)&&b[0]+b[2]!=500)throw new AssertionError("Right anchor moved");
            if((corner==3||corner==5)&&b[0]!=100)throw new AssertionError("Left anchor moved");
            if((corner==2||corner==3)&&b[1]+b[3]!=700)throw new AssertionError("Bottom anchor moved");
            if((corner==4||corner==5)&&b[1]!=100)throw new AssertionError("Top anchor moved");
        }
        if(OverlayGeometry.swipe(0,60,48)!=1||OverlayGeometry.swipe(0,-60,48)!=-1||OverlayGeometry.swipe(0,20,48)!=0||OverlayGeometry.swipe(100,60,48)!=0)throw new AssertionError("Swipe classification");
        for(int i=0;i<10000;i++){
            int sw=300+rng.nextInt(1800),sh=400+rng.nextInt(1800);
            int[] shape=OverlayGeometry.phoneSize(sw,sh,220+rng.nextInt(800));
            if(shape[0]>sw||shape[1]>sh||Math.abs(shape[1]-shape[0]*OverlayGeometry.PHONE_RATIO)>.51f)throw new AssertionError("Phone aspect");
            int x=(sw-shape[0])/2,y=(sh-shape[1])/2;
            for(int corner=2;corner<=5;corner++){
                int[] b=OverlayGeometry.resizePhone(corner,x,y,shape[0],shape[1],rng.nextInt(1200)-600,rng.nextInt(1200)-600,sw,sh,220);
                if(b[0]<0||b[1]<0||b[0]+b[2]>sw||b[1]+b[3]>sh||Math.abs(b[3]-b[2]*OverlayGeometry.PHONE_RATIO)>.51f)throw new AssertionError("Phone resize");
            }
        }
        System.out.println("PASS: window bounds, corner anchors, swipes, 10,000 phone sizes and 40,000 fixed-ratio corner resizes");
    }
}
