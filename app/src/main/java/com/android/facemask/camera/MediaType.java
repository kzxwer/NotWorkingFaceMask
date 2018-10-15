package com.android.facemask.camera;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static com.android.facemask.camera.MediaType.IMAGE;
import static com.android.facemask.camera.MediaType.VIDEO;

/**
 * Created by Pankaj Soni <pankajsoni@softwarejoint.com> on 03/03/18.
 * Copyright (c) 2018 Software Joint. All rights reserved.
 */
@Retention(RetentionPolicy.CLASS)
@IntDef({VIDEO, IMAGE})
public @interface MediaType {
    int VIDEO = 1;
    int IMAGE = 2;
}