package com.badlogic.gdx.backends.svm;

import com.badlogic.gdx.Net;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.badlogic.gdx.utils.Array;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import com.badlogic.gdx.scenes.scene2d.actions.*;

import java.nio.*;
import java.util.ArrayList;
import java.util.Collections;

public class ConfigCollectionFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
            JNIRuntimeAccess.register(String.class);
            JNIRuntimeAccess.register(DoubleBuffer.class, IntBuffer.class, FloatBuffer.class, Buffer.class, LongBuffer.class, CharBuffer.class, ByteBuffer.class, ShortBuffer.class);
            RuntimeReflection.register(GlyphLayout.class.getConstructor());
            RuntimeReflection.register(GlyphLayout.GlyphRun.class.getConstructor());
            RuntimeReflection.register(Color.class.getConstructor());
            RuntimeReflection.register(Table.DebugRect.class.getConstructor());
            RuntimeReflection.register(AddAction.class.getConstructor(), RemoveAction.class.getConstructor(), MoveToAction.class.getConstructor(), MoveByAction.class.getConstructor(), SizeToAction.class.getConstructor(), SizeByAction.class.getConstructor(), ScaleToAction.class.getConstructor(), ScaleByAction.class.getConstructor(), RotateToAction.class.getConstructor(), RotateByAction.class.getConstructor(), ColorAction.class.getConstructor(), AlphaAction.class.getConstructor(), VisibleAction.class.getConstructor(), TouchableAction.class.getConstructor(), RemoveActorAction.class.getConstructor(), DelayAction.class.getConstructor(), TimeScaleAction.class.getConstructor(), SequenceAction.class.getConstructor(), ParallelAction.class.getConstructor(), RepeatAction.class.getConstructor(), RunnableAction.class.getConstructor(), LayoutAction.class.getConstructor(), AfterAction.class.getConstructor(), AddListenerAction.class.getConstructor(), RemoveListenerAction.class.getConstructor(), Array.class.getConstructor(), Rectangle.class.getConstructor(), ChangeListener.ChangeEvent.class.getConstructor(), Net.HttpRequest.class.getConstructor(), InputEvent.class.getConstructor(), Stage.TouchFocus.class.getConstructor(), FocusListener.FocusEvent.class.getConstructor());
            RuntimeReflection.register(BitmapFont.class);
            RuntimeReflection.register(Color.class);
            RuntimeReflection.register(Button.ButtonStyle.class, CheckBox.CheckBoxStyle.class, ImageButton.ImageButtonStyle.class, ImageTextButton.ImageTextButtonStyle.class, Label.LabelStyle.class, List.ListStyle.class, ProgressBar.ProgressBarStyle.class, ScrollPane.ScrollPaneStyle.class, SelectBox.SelectBoxStyle.class, Skin.TintedDrawable.class, Slider.SliderStyle.class, SplitPane.SplitPaneStyle.class, Table.DebugRect.class, TextButton.TextButtonStyle.class, TextField.TextFieldFilter.DigitsOnlyFilter.class, TextField.DefaultOnscreenKeyboard.class, TextField.TextFieldStyle.class, TextTooltip.TextTooltipStyle.class, Touchpad.TouchpadStyle.class, Tree.TreeStyle.class, Value.Fixed.class, Window.WindowStyle.class);
            RuntimeReflection.register(Button.ButtonStyle.class.getConstructor(), CheckBox.CheckBoxStyle.class.getConstructor(), ImageButton.ImageButtonStyle.class.getConstructor(), ImageTextButton.ImageTextButtonStyle.class.getConstructor(), Label.LabelStyle.class.getConstructor(), List.ListStyle.class.getConstructor(), ProgressBar.ProgressBarStyle.class.getConstructor(), ScrollPane.ScrollPaneStyle.class.getConstructor(), SelectBox.SelectBoxStyle.class.getConstructor(), Skin.TintedDrawable.class.getConstructor(), Slider.SliderStyle.class.getConstructor(), SplitPane.SplitPaneStyle.class.getConstructor(), Table.DebugRect.class.getConstructor(), TextButton.TextButtonStyle.class.getConstructor(), TextField.TextFieldFilter.DigitsOnlyFilter.class.getConstructor(), TextField.DefaultOnscreenKeyboard.class.getConstructor(), TextField.TextFieldStyle.class.getConstructor(), TextTooltip.TextTooltipStyle.class.getConstructor(), Touchpad.TouchpadStyle.class.getConstructor(), Tree.TreeStyle.class.getConstructor(), Value.Fixed.class.getConstructor(float.class), Window.WindowStyle.class.getConstructor());
            RuntimeReflection.register(concatArrays(Button.ButtonStyle.class.getDeclaredFields(), CheckBox.CheckBoxStyle.class.getDeclaredFields(), ImageButton.ImageButtonStyle.class.getDeclaredFields(), ImageTextButton.ImageTextButtonStyle.class.getDeclaredFields(), Label.LabelStyle.class.getDeclaredFields(), List.ListStyle.class.getDeclaredFields(), ProgressBar.ProgressBarStyle.class.getDeclaredFields(), ScrollPane.ScrollPaneStyle.class.getDeclaredFields(), SelectBox.SelectBoxStyle.class.getDeclaredFields(), Skin.TintedDrawable.class.getDeclaredFields(), Slider.SliderStyle.class.getDeclaredFields(), SplitPane.SplitPaneStyle.class.getDeclaredFields(), Table.DebugRect.class.getDeclaredFields(), TextButton.TextButtonStyle.class.getDeclaredFields(), TextField.TextFieldFilter.DigitsOnlyFilter.class.getDeclaredFields(), TextField.DefaultOnscreenKeyboard.class.getDeclaredFields(), TextField.TextFieldStyle.class.getDeclaredFields(), TextTooltip.TextTooltipStyle.class.getDeclaredFields(), Touchpad.TouchpadStyle.class.getDeclaredFields(), Tree.TreeStyle.class.getDeclaredFields(), Value.Fixed.class.getDeclaredFields(), Window.WindowStyle.class.getDeclaredFields()));
        }catch (NoSuchMethodException e){
            e.printStackTrace();
        }
    }

    public <T> T[] concatArrays(T[]... arrays) {
        ArrayList<T> list = new ArrayList<>();
        for (T[] array : arrays) {
            Collections.addAll(list, array);
        }
        return list.toArray(arrays[0]);
    }
}
