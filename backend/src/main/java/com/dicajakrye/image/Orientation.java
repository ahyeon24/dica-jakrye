package com.dicajakrye.image;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/** EXIF Orientation(1~8)에 맞게 픽셀을 돌리거나 뒤집는다. 1이거나 모르는 값이면 그대로 */
final class Orientation {

    private Orientation() {
    }

    static BufferedImage apply(BufferedImage src, int orientation) {
        if (orientation < 2 || orientation > 8) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5; // 5~8은 90도 돌아가 가로·세로가 바뀐다
        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.translate(w, 0); t.scale(-1, 1); }                          // 좌우 반전
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }                      // 180도
            case 4 -> { t.translate(0, h); t.scale(1, -1); }                          // 상하 반전
            case 5 -> { t.rotate(Math.PI / 2); t.scale(1, -1); }                     // 반전 + 90도
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }                  // 시계 방향 90도
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }              // 반시계 90도
            default -> { }
        }
        BufferedImage dst = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.drawImage(src, t, null);
        } finally {
            g.dispose();
        }
        return dst;
    }
}
