package camera;

import java.awt.Point;
import main.GamePanel;
import utils.KeyHandler;

public class RTSCamera {
    private final double normalSpeed;
    private final double shiftSpeed;
    private double cameraSpeed;
    private double zoomLevel;
    private double zoomChange;
    private final double minZoom;
    private final double maxZoom;
    private final KeyHandler keyHandler;
    private final int screenX;
    private final int screenY;
    private final Point coords;

    public RTSCamera(KeyHandler keyHandler) {
        this.zoomLevel = 1;
        this.zoomChange = 0.2;
        this.minZoom = 0.25;
        this.maxZoom = 4.0;
        this.normalSpeed = 4;
        this.shiftSpeed = normalSpeed * 2;
        this.cameraSpeed = normalSpeed;
        this.keyHandler = keyHandler;
        this.screenX = GamePanel.SCREEN_WIDTH / 2;
        this.screenY = GamePanel.SCREEN_HEIGHT / 2;
        this.coords = new Point(0, 0);
    }

    public void update() {
        boolean forwardAndBackwardCancel = keyHandler.isForwardPressed() && keyHandler.isBackwardPressed();
        boolean leftAndRightCancel = keyHandler.isLeftPressed() && keyHandler.isRightPressed();
        if (keyHandler.isShiftPressed()) {
            cameraSpeed = shiftSpeed;
        } else {
            cameraSpeed = normalSpeed;
        }
        if (keyHandler.isForwardPressed() && !forwardAndBackwardCancel) {
            coords.y -= cameraSpeed;
        }
        if (keyHandler.isLeftPressed() && !leftAndRightCancel) {
            coords.x -= cameraSpeed;
        }
        if (keyHandler.isBackwardPressed() && !forwardAndBackwardCancel) {
            coords.y += cameraSpeed;
        }
        if (keyHandler.isRightPressed() && !leftAndRightCancel) {
            coords.x += cameraSpeed;
        }
    }

    public Point getCoords() { return coords; }

    public int getX() { return coords.x; }

    public int getY() { return coords.y; }

    public double getCameraSpeed() { return cameraSpeed; }

    public void setCameraSpeed(int cameraSpeed) { this.cameraSpeed = cameraSpeed; }

    public double getZoomLevel() { return zoomLevel; }

    public void setZoomLevel(double zoom) {
        // clamp zoom to allowed range
        if (zoom < minZoom) {
            this.zoomLevel = minZoom;
        } else if (zoom > maxZoom) {
            this.zoomLevel = maxZoom;
        } else {
            this.zoomLevel = zoom;
        }
    }

    public void increaseZoom() { setZoomLevel(zoomLevel + zoomChange); }

    public void decreaseZoom() { setZoomLevel(zoomLevel - zoomChange); }

    public void setZoomChange(double zoomChange) { this.zoomChange = zoomChange; }

    public void zoomIn() { setZoomLevel(zoomLevel + zoomChange); }

    public void zoomOut() { setZoomLevel(zoomLevel - zoomChange); }

    public double getZoomChange() { return zoomChange; }

    public double getMinZoom() { return minZoom; }

    public double getMaxZoom() { return maxZoom; }

    public int getScreenX() { return screenX; }

    public int getScreenY() { return screenY; }
}
