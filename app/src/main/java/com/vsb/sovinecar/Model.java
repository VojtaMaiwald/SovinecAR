package com.vsb.sovinecar;

import android.widget.ImageView;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.rendering.Renderable;

import java.util.ArrayList;
import java.util.List;

public class Model {

    String uri;
    String name;
    Renderable renderable;
    ImageView imageView;
    List<AnchorNode> anchorNodes;

    public Model(String name, String uri, ImageView imageView) {
        this.name = name;
        this.uri = uri;
        this.imageView = imageView;

        this.anchorNodes = new ArrayList<AnchorNode>();
    }

    public String getUri() {
        return uri;
    }

    public String getName() {
        return name;
    }

    public Renderable getRenderable() {
        return renderable;
    }

    public void setRenderable(Renderable renderable) {
        this.renderable = renderable;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public void addAnchorNode(AnchorNode anchorNode) {
        this.anchorNodes.add(anchorNode);
    }

    public void delete() {
        for (AnchorNode anchorNode : this.anchorNodes) {
        }

        anchorNodes = new ArrayList<AnchorNode>();
    }
}
