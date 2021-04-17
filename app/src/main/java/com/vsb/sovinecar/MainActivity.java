package com.vsb.sovinecar;

import android.app.ActionBar;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;


import com.google.android.filament.Engine;
import com.google.android.filament.filamat.MaterialBuilder;
import com.google.android.filament.filamat.MaterialPackage;
import com.google.android.filament.Engine;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.EngineInstance;
import com.google.ar.sceneform.rendering.ExternalTexture;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.RenderableInstance;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.Attributes;

public class MainActivity extends AppCompatActivity implements
        BaseArFragment.OnTapArPlaneListener,
        View.OnClickListener,
        BaseArFragment.OnSessionInitializationListener{

    private ArFragment arFragment;

    private LinkedList<Model> modelList;
    private Model activeModel = null;

    private boolean inTagMode = false;

    private Scene scene;
    private boolean isDetected = false;

    private boolean configureSession = false;
    private Session session;

    private Renderable plainVideoModel;
    private Material plainVideoMaterial;
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().hide();

        setContentView(R.layout.activity_main);

        getSupportFragmentManager().addFragmentOnAttachListener((fragmentManager, fragment) -> {
            if (fragment.getId() == R.id.arFragment) {
                this.arFragment = (ArFragment) fragment;
                this.arFragment.setOnTapArPlaneListener(MainActivity.this);
                this.arFragment.setOnSessionInitializationListener(MainActivity.this);
            }
        });

        if (savedInstanceState == null) {
            if (Sceneform.isSupported(this)) {
                getSupportFragmentManager().beginTransaction()
                    .add(R.id.arFragment, ArFragment.class, null)
                    .commit();
            }
        }

        modelList = new LinkedList<Model>();
        modelList.add(new Model(ModelName.FOX, ModelUri.FOX, findViewById(R.id.foxView)));
        modelList.add(new Model(ModelName.IRONMAN, ModelUri.IRONMAN, findViewById(R.id.ironmanView)));
        modelList.add(new Model(ModelName.MERCEDES, ModelUri.MERCEDES, findViewById(R.id.mercedesView)));
        modelList.add(new Model(ModelName.MUSHROOM, ModelUri.MUSHROOM, findViewById(R.id.mushroomView)));

        for (Model model : modelList) {
            loadModel(model);
            model.getImageView().setOnClickListener(this);
        }

        loadMatrixModel();
        loadMatrixMaterial();
    }

    private void loadMatrixModel() {

        WeakReference<MainActivity> weakActivity = new WeakReference<>(this);
        ModelRenderable.builder()
            .setSource(this, Uri.parse(ModelUri.MATRIX))
            .setIsFilamentGltf(true)
            .build()
            .thenAccept(model -> {
                MainActivity activity = weakActivity.get();
                if (activity != null) {
                    activity.plainVideoModel = model;
                }
            })
            .exceptionally(
                throwable -> {
                    Toast.makeText(this, "Unable to load renderable", Toast.LENGTH_LONG).show();
                    return null;
                });
    }

    private void loadMatrixMaterial() {
        WeakReference<MainActivity> weakActivity = new WeakReference<>(this);
        Engine filamentEngine = EngineInstance.getEngine().getFilamentEngine();

        MaterialBuilder.init();
        MaterialBuilder materialBuilder = new MaterialBuilder()
            .platform(MaterialBuilder.Platform.MOBILE)
            .name("Plain Video Material")
            .require(MaterialBuilder.VertexAttribute.UV0)
            .shading(MaterialBuilder.Shading.UNLIT)
            .doubleSided(true)
            .samplerParameter(MaterialBuilder.SamplerType.SAMPLER_EXTERNAL, MaterialBuilder.SamplerFormat.FLOAT, MaterialBuilder.SamplerPrecision.DEFAULT, "videoTexture")
            .optimization(MaterialBuilder.Optimization.NONE);

        MaterialPackage plainVideoMaterialPackage = materialBuilder
            .blending(MaterialBuilder.BlendingMode.OPAQUE)
            .material("void material(inout MaterialInputs material) {\n" + "    prepareMaterial(material);\n" +
                      "    material.baseColor = texture(materialParams_videoTexture, getUV0()).rgba;\n" + "}\n")
            .build(filamentEngine);
        if (plainVideoMaterialPackage.isValid()) {
            ByteBuffer buffer = plainVideoMaterialPackage.getBuffer();
            Material.builder()
                .setSource(buffer)
                .build()
                .thenAccept(material -> {
                    MainActivity activity = weakActivity.get();
                    if (activity != null) {
                        activity.plainVideoMaterial = material;
                    }
                })
                .exceptionally(
                    throwable -> {
                        Toast.makeText(this, "Unable to load material", Toast.LENGTH_LONG).show();
                        return null;
                    });
        }
        MaterialBuilder.shutdown();
    }

    @Override
    public void onSessionInitialization(Session session) {
        this.scene = this.arFragment.getArSceneView().getScene();
        this.scene.addOnUpdateListener(this::onUpdate);

        setupSession();
    }

    private void setupSession() {
        if (session == null) {
            try {
                session = new Session(this);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            configureSession = true;
        }

        if (configureSession) {

            Config config = new Config(session);
            if (!buildDatabase(config)) {
                Toast.makeText(this, "Database error", Toast.LENGTH_SHORT).show();
            }
            config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
            session.configure(config);

            configureSession = false;
            this.arFragment.getArSceneView().setupSession(session);
        }

        try {
            session.resume();
            this.arFragment.getArSceneView().resume();
        }
        catch (CameraNotAvailableException e) {
            e.printStackTrace();
            session = null;
        }
    }

    private boolean buildDatabase(Config config) {
        AugmentedImageDatabase augmentedImageDatabase;

        try {
            InputStream inputStream = getAssets().open("tagdb.imgdb");
            augmentedImageDatabase = AugmentedImageDatabase.deserialize(session, inputStream);
            config.setAugmentedImageDatabase(augmentedImageDatabase);
            return true;
        }
        catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void onUpdate(FrameTime frameTime) {
        if (!inTagMode || isDetected)
            return;

        Frame frame = this.arFragment.getArSceneView().getArFrame();
        try {
            Collection<AugmentedImage> augmentedImageCollection = frame.getUpdatedTrackables(AugmentedImage.class);

            for (AugmentedImage image : augmentedImageCollection) {
                if (image.getTrackingState() == TrackingState.TRACKING) {
                    if (image.getName().equals("rabbit.png")) {
                        Log.println(Log.ASSERT, "lol", "Rabbit detected");
                        ArNode arNode = new ArNode(this, ModelUri.RABBIT);
                        arNode.setImage(image, true);
                        this.arFragment.getArSceneView().getScene().addChild(arNode);
                        isDetected = true;
                    }
                    /*
                    else if (image.getName().equals("key.png")) {
                        ArNode arNode = new ArNode(this, ModelUri.FOX);
                        arNode.setImage(image);
                        this.arFragment.getArSceneView().getScene().addChild(arNode);
                        isDetected = true;
                    }
                    */
                    else if (image.getName().equals("vault.png")) {
                        Log.println(Log.ASSERT, "lol", "Vault detected");
                        isDetected = true;

                        WeakReference<MainActivity> weakActivity = new WeakReference<>(this);
                        ModelRenderable.builder()
                            .setSource(this, Uri.parse(ModelUri.VAULT))
                            .setIsFilamentGltf(true)
                            .build()
                            .thenAccept(model -> {
                                MainActivity activity = weakActivity.get();
                                if (activity != null) {

                                    model.setShadowCaster(false);
                                    model.setShadowReceiver(false);

                                    ArNode arNode = new ArNode(this, ModelUri.VAULT);
                                    arNode.setImage(image, false);
                                    this.arFragment.getArSceneView().getScene().addChild(arNode);

                                    // Create the transformable model and add it to the anchor.
                                    TransformableNode transNode = new TransformableNode(arFragment.getTransformationSystem());
                                    transNode.setParent(arNode);
                                    transNode.setRenderable(model);
                                    transNode.getRenderableInstance().animate(true).start();
                                }
                            })
                            .exceptionally(throwable -> {
                                Toast.makeText(this, "Nelze načíst model " + ModelUri.VAULT, Toast.LENGTH_LONG).show();
                                return null;
                            });
                    }


                    else if (image.getName().equals("matrix.png")) {
                        Log.println(Log.ASSERT, "lol", "Matrix detected");
                        isDetected = true;

                        WeakReference<MainActivity> weakActivity = new WeakReference<>(this);
                        ModelRenderable.builder()
                            .setSource(this, Uri.parse(ModelUri.MATRIX))
                            .setIsFilamentGltf(true)
                            .build()
                            .thenAccept(model -> {
                                MainActivity activity = weakActivity.get();
                                if (activity != null) {

                                    model.setShadowCaster(false);
                                    model.setShadowReceiver(false);

                                    ArNode arNode = new ArNode(this, ModelUri.MATRIX);
                                    arNode.setImage(image, false);
                                    arNode.setWorldScale(new Vector3(image.getExtentX(), 1f, image.getExtentZ()));
                                    this.arFragment.getArSceneView().getScene().addChild(arNode);

                                    // Create the transformable model and add it to the anchor.
                                    TransformableNode transNode = new TransformableNode(arFragment.getTransformationSystem());
                                    transNode.setParent(arNode);
                                    transNode.setLocalRotation(Quaternion.axisAngle(new Vector3(0, 1f, 0), 180f));
                                    //transNode.getRenderableInstance().animate(true).start();

                                    ExternalTexture externalTexture = new ExternalTexture();
                                    //MediaPlayer mediaPlayer;
                                    RenderableInstance renderableInstance;
                                    transNode.setRenderable(plainVideoModel);
                                    renderableInstance = transNode.getRenderableInstance();
                                    renderableInstance.setMaterial(plainVideoMaterial);
                                    mediaPlayer = MediaPlayer.create(this, R.raw.matrix);

                                    renderableInstance.getMaterial().setExternalTexture("videoTexture", externalTexture);
                                    mediaPlayer.setLooping(true);
                                    mediaPlayer.setSurface(externalTexture.getSurface());
                                    mediaPlayer.start();
                                }
                            })
                            .exceptionally(throwable -> {
                                Toast.makeText(this, "Nelze načíst model " + ModelUri.MATRIX, Toast.LENGTH_LONG).show();
                                return null;
                            });
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View view) {
        for (Model model : modelList) {
            if (model.getImageView().getId() == view.getId()) {
                activeModel = model;
                model.getImageView().setBackgroundColor(Color.parseColor("#99000000"));
            }
            else {
                model.getImageView().setBackgroundColor(Color.parseColor("#00000000"));
            }
        }
    }

    public void loadModel(Model modelInstance) {
        WeakReference<MainActivity> weakActivity = new WeakReference<>(this);
        ModelRenderable.builder()
            .setSource(this, Uri.parse(modelInstance.getUri()))
            .setIsFilamentGltf(true)
            .build()
            .thenAccept(model -> {
                MainActivity activity = weakActivity.get();
                if (activity != null) {
                    modelInstance.setRenderable(model);
                    if (modelInstance.getName() == ModelName.FOX) {
                        activity.activeModel = modelInstance;
                        modelInstance.getImageView().setBackgroundColor(Color.parseColor("#99000000"));
                    }
                }
            })
            .exceptionally(throwable -> {
                Toast.makeText(this, "Nelze načíst model " + modelInstance.getUri(), Toast.LENGTH_LONG).show();
                return null;
            });
    }

    @Override
    public void onTapPlane(HitResult hitResult, Plane plane, MotionEvent motionEvent) {

        if (activeModel == null || activeModel.getRenderable() == null) {
            Toast.makeText(this, "Pro umisťování modelů v prostoru přepněte mód aplikace", Toast.LENGTH_LONG).show();
            return;
        }

        // Create the Anchor.
        Anchor anchor = hitResult.createAnchor();
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        // Create the transformable model and add it to the anchor.
        TransformableNode transNode = new TransformableNode(arFragment.getTransformationSystem());
        transNode.setParent(anchorNode);
        transNode.setRenderable(activeModel.getRenderable());
        transNode.getRenderableInstance().animate(true).start();
        transNode.select();

        // Create Node and set its parent to transformable node to place it on detected plane
        Node node = new Node();
        node.setParent(transNode);
        node.setEnabled(false);
        node.setLocalPosition(new Vector3(0.0f, 1.0f, 0.0f));
        node.setEnabled(true);

        activeModel.addAnchorNode(anchorNode);
    }

    public void onFabSwitchBtnClick(View view) {
        this.switchMode(!this.inTagMode);
    }

    public void onFabClearBtnClick(View view) {
        List<Node> children = new ArrayList<>(arFragment.getArSceneView().getScene().getChildren());
        for (Node node : children) {
            if (node instanceof AnchorNode) {
                if (((AnchorNode) node).getAnchor() != null) {
                    ((AnchorNode) node).getAnchor().detach();
                }
            }
        }
        isDetected = false;
        if (mediaPlayer != null)
            mediaPlayer.stop();
    }

    private void switchMode(boolean toTagMode) {
        this.inTagMode = toTagMode;

        if (toTagMode) {
            findViewById(R.id.scrollView).setVisibility(View.GONE);
            ((FloatingActionButton)findViewById(R.id.switchButton)).setImageResource(R.drawable.round_view_in_ar_24);
            this.activeModel = null;
        }
        else {
            findViewById(R.id.scrollView).setVisibility(View.VISIBLE);
            ((FloatingActionButton)findViewById(R.id.switchButton)).setImageResource(R.drawable.round_qr_code_scanner_24);
            this.activeModel = this.modelList.get(0);
            for (Model model : modelList) {
                if (model.getName() == ModelName.FOX)
                    model.getImageView().setBackgroundColor(Color.parseColor("#99000000"));
                else
                    model.getImageView().setBackgroundColor(Color.parseColor("#00000000"));
            }
        }
    }

}