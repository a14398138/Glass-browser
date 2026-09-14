package dev.glass.browser;

/** Keep the close and drag controls reachable even after a rotation or resize. */
final class OverlayGeometry {
    static int[] fit(int x,int y,int width,int height,int screenW,int screenH,int minW,int minH) {
        screenW=Math.max(1,screenW);screenH=Math.max(1,screenH);
        width=Math.max(Math.min(minW,screenW),Math.min(width,screenW));
        height=Math.max(Math.min(minH,screenH),Math.min(height,screenH));
        x=Math.max(0,Math.min(x,screenW-width));y=Math.max(0,Math.min(y,screenH-height));
        return new int[]{x,y,width,height};
    }
    static int swipe(float dx,float dy,int threshold) {
        if(Math.abs(dy)<threshold||Math.abs(dy)<Math.abs(dx)*1.25f)return 0;
        return dy>0?1:-1;
    }
    static int[] resize(int region,int x,int y,int w,int h,int dx,int dy,int sw,int sh,int minW,int minH) {
        if(region==1)return fit(x+dx,y+dy,w,h,sw,sh,minW,minH);
        int right=x+w,bottom=y+h;
        minW=Math.min(minW,sw);minH=Math.min(minH,sh);
        if(region==2||region==4)x=Math.max(0,Math.min(x+dx,right-minW));
        else right=Math.min(sw,Math.max(right+dx,x+minW));
        if(region==2||region==3)y=Math.max(0,Math.min(y+dy,bottom-minH));
        else bottom=Math.min(sh,Math.max(bottom+dy,y+minH));
        return new int[]{x,y,right-x,bottom-y};
    }
    static final float PHONE_RATIO=3f/2f;
    static int[] phoneSize(int sw,int sh,int preferredWidth){
        int w=Math.max(1,Math.min(preferredWidth,Math.min(sw,(int)(sh/PHONE_RATIO))));
        return new int[]{w,Math.max(1,Math.round(w*PHONE_RATIO))};
    }
    static int[] resizePhone(int region,int x,int y,int w,int h,int dx,int dy,int sw,int sh,int minWidth){
        if(region==1)return fit(x+dx,y+dy,w,h,sw,sh,Math.min(minWidth,w),h);
        boolean left=region==2||region==4,top=region==2||region==3;
        int anchorX=left?x+w:x,anchorY=top?y+h:y;
        float horizontal=left?-dx:dx,vertical=(top?-dy:dy)/PHONE_RATIO;
        int wanted=Math.round(w+(Math.abs(horizontal)>=Math.abs(vertical)?horizontal:vertical));
        int maxW=Math.min(left?anchorX:sw-anchorX,(int)((top?anchorY:sh-anchorY)/PHONE_RATIO));
        int width=Math.max(Math.min(minWidth,maxW),Math.min(wanted,maxW));
        int height=Math.round(width*PHONE_RATIO);
        return new int[]{left?anchorX-width:anchorX,top?anchorY-height:anchorY,width,height};
    }
}
