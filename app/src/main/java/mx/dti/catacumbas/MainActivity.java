package mx.dti.catacumbas;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {
  private static final String API="https://api.github.com/repos/saulsbustos/catacumbas-dti/contents?ref=main";
  private final Handler ui=new Handler();
  private final Random rng=new Random();
  private TextView status, count;
  private SurfaceView surface;
  private Camera camera;
  private MediaPlayer player;
  private File library;
  private final ArrayList<File> tracks=new ArrayList<>();
  private boolean watching=false, playing=false, surfaceReady=false, syncing=false;
  private int sensitivity=35, cooldown=5, last=-1;
  private long nextAllowed=0, lastFrame=0;
  private byte[] previous;
  private int frameWidth=320,frameHeight=240;
  private float oldBrightness=-1;

  @Override public void onCreate(Bundle b){super.onCreate(b);
    library=new File(getFilesDir(),"audios"); library.mkdirs();
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(14,14,14,14);root.setBackgroundColor(0xff19141d);
    ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    TextView title=text("💀 Catacumbas DTI",24);root.addView(title);
    count=text("",16);root.addView(count);
    status=text("Preparando biblioteca...",16);root.addView(status);
    Button sync=button("Sincronizar sonidos");root.addView(sync);sync.setOnClickListener(v->syncSounds());
    surface=new SurfaceView(this);root.addView(surface,new LinearLayout.LayoutParams(-1,350));surface.getHolder().addCallback(this);
    Button start=button("Iniciar vigilancia");root.addView(start);start.setOnClickListener(v->{if(watching){stopWatch();start.setText("Iniciar vigilancia");}else{if(tracks.isEmpty()){say("Primero sincroniza los sonidos.");return;}if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},4);return;}startWatch();if(watching)start.setText("Detener vigilancia");}});
    Button test=button("Probar sonido");root.addView(test);test.setOnClickListener(v->playRandom(true));
    TextView sensLabel=text("Sensibilidad: 35",16);root.addView(sensLabel);
    SeekBar sens=new SeekBar(this);sens.setMax(85);sens.setProgress(30);root.addView(sens);sens.setOnSeekBarChangeListener(change(p->{sensitivity=p+5;sensLabel.setText("Sensibilidad: "+sensitivity);}));
    TextView coolLabel=text("Espera: 5 segundos",16);root.addView(coolLabel);
    SeekBar cool=new SeekBar(this);cool.setMax(18);cool.setProgress(3);root.addView(cool);cool.setOnSeekBarChangeListener(change(p->{cooldown=p+2;coolLabel.setText("Espera: "+cooldown+" segundos");}));
    TextView brightLabel=text("Brillo: 10%",16);root.addView(brightLabel);
    SeekBar bright=new SeekBar(this);bright.setMax(90);bright.setProgress(0);root.addView(bright);bright.setOnSeekBarChangeListener(change(p->{float level=(p+10)/100f;brightLabel.setText("Brillo: "+(p+10)+"%");if(watching)setBrightness(level);}));
    loadLibrary();syncSounds();
  }
  interface Progress{void update(int p);}
  private SeekBar.OnSeekBarChangeListener change(Progress p){return new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int v,boolean from){p.update(v);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}};}
  private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xfff7e7ed);t.setPadding(4,9,4,9);return t;}
  private Button button(String s){Button b=new Button(this);b.setText(s);return b;}
  private void say(String s){runOnUiThread(()->status.setText(s));}
  private synchronized void loadLibrary(){File[] fs=library.listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".mp3"));tracks.clear();if(fs!=null){Arrays.sort(fs,Comparator.comparing(File::getName));tracks.addAll(Arrays.asList(fs));}runOnUiThread(()->count.setText("Sonidos disponibles: "+tracks.size()));}
  private void syncSounds(){if(syncing)return;syncing=true;say("Consultando GitHub...");new Thread(()->{
    File stage=new File(getCacheDir(),"audio-stage");stage.mkdirs();
    try{
      HttpURLConnection c=connection(API);JSONArray a=new JSONArray(readString(c));c.disconnect();
      ArrayList<JSONObject> items=new ArrayList<>();HashSet<String> wanted=new HashSet<>();
      for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);String name=o.optString("name");if("file".equals(o.optString("type"))&&name.matches("(?i)[a-zA-Z0-9._-]+\\.mp3")&&!name.startsWith(".")){items.add(o);wanted.add(name);}}
      if(items.isEmpty())throw new IOException("No hay MP3 en la raíz del repositorio.");
      android.content.SharedPreferences prefs=getSharedPreferences("hashes",0);
      HashMap<String,File> downloaded=new HashMap<>();
      for(JSONObject o:items){String name=o.getString("name"),sha=o.getString("sha");File dest=new File(library,name);
        if(dest.isFile()&&dest.length()>0&&sha.equals(prefs.getString(name,"")))continue;
        say("Descargando "+name+"...");File temp=new File(stage,name+".tmp");download(o.getString("download_url"),temp);if(temp.length()<128)throw new IOException("Archivo demasiado pequeño: "+name);downloaded.put(name,temp);
      }
      // Only commit after every required download succeeds.
      for(JSONObject o:items){String name=o.getString("name");File temp=downloaded.get(name);if(temp!=null){File dest=new File(library,name);File bak=new File(library,name+".bak");if(bak.exists())bak.delete();if(dest.exists()&&!dest.renameTo(bak))throw new IOException("No se pudo respaldar "+name);if(!temp.renameTo(dest)){if(bak.exists())bak.renameTo(dest);throw new IOException("No se pudo guardar "+name);}bak.delete();}prefs.edit().putString(name,o.getString("sha")).apply();}
      // Do not delete an audio currently playing; defer library reload until idle.
      for(File f:Objects.requireNonNull(library.listFiles()))if(f.getName().endsWith(".mp3")&&!wanted.contains(f.getName())){if(playing){continue;}f.delete();prefs.edit().remove(f.getName()).apply();}
      if(!playing)loadLibrary();else ui.postDelayed(()->{if(!playing)loadLibrary();},1500);
      say("Sincronización completada: "+items.size()+" sonidos en GitHub.");
    }catch(Exception e){say("Sincronización falló; se conservan audios locales. "+e.getMessage());}
    finally{syncing=false;}
  }).start();}
  private HttpURLConnection connection(String url)throws IOException{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setRequestProperty("User-Agent","CatacumbasDTI/1.0");c.setRequestProperty("Accept","application/vnd.github+json");if(c.getResponseCode()!=200){int code=c.getResponseCode();c.disconnect();throw new IOException("HTTP "+code);}return c;}
  private String readString(HttpURLConnection c)throws IOException{try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>500000)throw new IOException("Catálogo demasiado grande");}return out.toString("UTF-8");}}
  private void download(String url,File f)throws IOException{HttpURLConnection c=connection(url);try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(f)){byte[] b=new byte[8192];int n;long total=0;while((n=in.read(b))!=-1){total+=n;if(total>25L*1024*1024)throw new IOException("MP3 supera 25 MB");out.write(b,0,n);}out.getFD().sync();}finally{c.disconnect();}}
  private void setBrightness(float b){WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=b;getWindow().setAttributes(p);}
  private void startWatch(){if(!surfaceReady){say("Espera a que se prepare la cámara.");return;}try{camera=Camera.open();Camera.Parameters p=camera.getParameters();Camera.Size chosen=p.getSupportedPreviewSizes().get(0);int diff=Integer.MAX_VALUE;for(Camera.Size s:p.getSupportedPreviewSizes()){int d=Math.abs(s.width-320)+Math.abs(s.height-240);if(d<diff){diff=d;chosen=s;}}frameWidth=chosen.width;frameHeight=chosen.height;p.setPreviewSize(frameWidth,frameHeight);camera.setParameters(p);camera.setPreviewDisplay(surface.getHolder());camera.setPreviewCallback(this);camera.startPreview();watching=true;previous=null;lastFrame=0;nextAllowed=System.currentTimeMillis()+1500;getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);oldBrightness=getWindow().getAttributes().screenBrightness;setBrightness(.10f);say("Vigilando movimiento...");}catch(Exception e){stopWatch();say("No se pudo iniciar la cámara: "+e.getMessage());}}
  private void stopWatch(){watching=false;if(camera!=null){try{camera.setPreviewCallback(null);camera.stopPreview();camera.release();}catch(Exception ignored){}camera=null;}getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(oldBrightness>=-1)setBrightness(oldBrightness);previous=null;say("Vigilancia detenida.");}
  @Override public void onPreviewFrame(byte[] data,Camera c){long now=System.currentTimeMillis();if(!watching||playing||now<nextAllowed||now-lastFrame<500)return;lastFrame=now;
    // Sample Y plane at sparse intervals; avoid per-frame bitmap allocations.
    int stride=8;int w=(frameWidth+stride-1)/stride,h=(frameHeight+stride-1)/stride;byte[] current=new byte[w*h];int n=0,changed=0;for(int y=0;y<frameHeight;y+=stride)for(int x=0;x<frameWidth;x+=stride){byte v=data[y*frameWidth+x];current[n]=v;if(previous!=null&&n<previous.length&&Math.abs((v&255)-(previous[n]&255))>24)changed++;n++;}
    if(previous!=null&&previous.length==current.length){float pct=100f*changed/current.length;float threshold=14f-sensitivity*.13f;if(pct>threshold){previous=null;nextAllowed=now+1500;runOnUiThread(()->playRandom(false));return;}}previous=current;
  }
  private void playRandom(boolean manual){if(playing)return;if(tracks.isEmpty()){say("No hay sonidos descargados.");return;}if(!manual&&!watching)return;int idx=rng.nextInt(tracks.size());if(tracks.size()>1&&idx==last)idx=(idx+1+rng.nextInt(tracks.size()-1))%tracks.size();last=idx;File f=tracks.get(idx);playing=true;previous=null;try{player=new MediaPlayer();player.setDataSource(f.getAbsolutePath());player.setOnCompletionListener(mp->finishAudio());player.setOnErrorListener((mp,what,extra)->{say("Error al reproducir "+f.getName());finishAudio();return true;});player.prepare();player.start();say("Reproduciendo: "+f.getName());}catch(Exception e){say("Error de audio: "+e.getMessage());finishAudio();}}
  private void finishAudio(){if(player!=null){try{player.reset();player.release();}catch(Exception ignored){}player=null;}playing=false;previous=null;nextAllowed=System.currentTimeMillis()+cooldown*1000L;if(watching)say("Vigilando (espera "+cooldown+" s)...");}
  @Override public void surfaceCreated(SurfaceHolder h){surfaceReady=true;}
  @Override public void surfaceChanged(SurfaceHolder h,int format,int w,int height){}
  @Override public void surfaceDestroyed(SurfaceHolder h){surfaceReady=false;if(watching)stopWatch();}
  @Override protected void onDestroy(){stopWatch();if(player!=null){player.release();player=null;}super.onDestroy();}
}
