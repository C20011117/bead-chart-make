import dev.pindou.pattern.pattern.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import kotlin.Unit;

/** Uses the APK's compiled pure core, not an alternate implementation. Desktop timings only. */
class IllustrationProbe {
    public static void main(String[] args) throws Exception {
        BufferedImage input=ImageIO.read(new File(args[0]));
        int w=input.getWidth(),h=input.getHeight();
        int[] pixels=input.getRGB(0,0,w,h,null,0,w);
        Path folder=Path.of(args[1]);Files.createDirectories(folder);
        StringBuilder report=new StringBuilder("Original user image; full-frame white padding. Actual compiled Kotlin core, desktop, not Android runtime.\n");
        var colors=Mard.INSTANCE.getPalette().getColors();
        for(int n:new int[]{32,48,64,128}) for(int mode=0;mode<2;mode++) {
            String name=mode==0?"photo":"illustration";
            var grid=new GridSize(n,n);
            double side=Math.max(w,h);
            var bounds=new CropRect((w-side)/2,(h-side)/2,side,side);
            var options=new ConversionOptions(mode==0?SamplingStyle.PHOTO:SamplingStyle.ILLUSTRATION);
            long start=System.nanoTime();
            Pattern pattern=PatternGenerator.INSTANCE.generate(pixels,w,h,bounds,grid,options,()->Unit.INSTANCE,p->Unit.INSTANCE);
            long ms=(System.nanoTime()-start)/1000000;
            if(Arrays.stream(pattern.counts()).sum()!=n*n) throw new AssertionError("counts");
            report.append(n+" "+name+": "+ms+"ms, colors="+pattern.usedColors().size()+", H7="+pattern.counts()[Mard.INSTANCE.indexOf("H7")]+"\n");
            BufferedImage preview=new BufferedImage(n*8,n*8,BufferedImage.TYPE_INT_RGB);
            StringBuilder csv=new StringBuilder();
            for(int y=0;y<n;y++) {
                for(int x=0;x<n;x++) {
                    int index=pattern.get(y*n+x);
                    if(x>0) csv.append(',');csv.append(index);
                    for(int yy=0;yy<8;yy++) for(int xx=0;xx<8;xx++) preview.setRGB(x*8+xx,y*8+yy,colors.get(index).getDisplayRgb());
                }
                csv.append('\n');
            }
            ImageIO.write(preview,"png",folder.resolve(name+"-"+n+".png").toFile());
            Files.writeString(folder.resolve(name+"-"+n+".csv"),csv);
        }
        Files.writeString(folder.resolve("report.txt"),report);
        System.out.println(report);
    }
}
