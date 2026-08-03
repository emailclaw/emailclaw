/*
 * The MIT License (MIT)
 * Copyright © 2026 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ai.emailclaw.emailclaw.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

public class SimpleToggleSwitch extends StackPane {
    private final Rectangle back = new Rectangle(36, 20);
    private final Circle thumb = new Circle(8);
    private boolean selected = false;

    public SimpleToggleSwitch() {
        back.setArcWidth(20);
        back.setArcHeight(20);
        back.setFill(Color.web("#e5e7eb"));
        thumb.setFill(Color.WHITE);
        thumb.setEffect(new DropShadow(2, Color.gray(0, 0.3)));
        StackPane.setAlignment(thumb, Pos.CENTER_LEFT);
        StackPane.setMargin(thumb, new Insets(0, 2, 0, 2));
        getChildren().addAll(back, thumb);
        setOnMouseClicked(
                e -> {
                    if (!isDisabled()) setSelected(!selected);
                });
        setCursor(Cursor.HAND);
        disableProperty().addListener((obs, oldV, newV) -> setOpacity(newV ? 0.5 : 1.0));
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean sel) {
        this.selected = sel;
        back.setFill(sel ? Color.web("#4ade80") : Color.web("#e5e7eb"));
        StackPane.setAlignment(thumb, sel ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
    }
}
