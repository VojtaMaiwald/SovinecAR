package com.vsb.sovinecar;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.widget.Toast;

import com.google.ar.core.AugmentedImage;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;

import java.util.concurrent.CompletableFuture;

public class ArNode extends AnchorNode {

    private AugmentedImage image;
    private CompletableFuture<ModelRenderable> modelRenderableCompletableFuture;

    public ArNode (Context context, String uri) {
        modelRenderableCompletableFuture = ModelRenderable.builder()
                .setSource(context, Uri.parse(uri))
                .setIsFilamentGltf(true)
                .build();
    }

    public void setImage (AugmentedImage image, boolean setParent) {
        this.image = image;

        if (!modelRenderableCompletableFuture.isDone()) {
            CompletableFuture.allOf(modelRenderableCompletableFuture)
                    .thenAccept((Void aVoid) -> { setImage(image, setParent); })
                    .exceptionally(throwable -> { return null; });
        }

        setAnchor(image.createAnchor(image.getCenterPose()));

        Node node = new Node();
        Pose pose = Pose.makeTranslation(0.0f, 0.0f, 0.05f);

        if (setParent)
            node.setParent(this);
        node.setLocalPosition(new Vector3(pose.tx(), pose.ty(), pose.tz()));
        node.setLocalRotation(new Quaternion(pose.qx(), pose.qy(), pose.qz(), pose.qw()));
        node.setRenderable(modelRenderableCompletableFuture.getNow(null));
    }

    public AugmentedImage getImage() {
        return this.image;
    }
}