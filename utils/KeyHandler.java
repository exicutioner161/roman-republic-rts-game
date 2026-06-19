package utils;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class KeyHandler extends KeyAdapter {
    private boolean forwardPressed;
    private boolean backwardPressed;
    private boolean leftPressed;
    private boolean rightPressed;
    private boolean shiftPressed;
    private boolean f11Pressed;
    private boolean modeTogglePressed;
    private boolean bridgeBuildPressed;
    private boolean fortBuildPressed;
    private boolean forageTogglePressed;
    private boolean buildCancelPressed;
    private boolean fullScreenToggleRequested;
    private boolean weaponModeToggleRequested;
    private boolean bridgeBuildRequested;
    private boolean fortBuildRequested;
    private boolean forageToggleRequested;
    private boolean buildCancelRequested;

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W -> forwardPressed = true;
            case KeyEvent.VK_A -> leftPressed = true;
            case KeyEvent.VK_S -> backwardPressed = true;
            case KeyEvent.VK_D -> rightPressed = true;
            case KeyEvent.VK_SHIFT -> shiftPressed = true;
            case KeyEvent.VK_F11 -> {
                fullScreenToggleRequested |= !f11Pressed;
                f11Pressed = true;
            }
            case KeyEvent.VK_R -> {
                weaponModeToggleRequested |= !modeTogglePressed;
                modeTogglePressed = true;
            }
            case KeyEvent.VK_B -> {
                bridgeBuildRequested |= !bridgeBuildPressed;
                bridgeBuildPressed = true;
            }
            case KeyEvent.VK_F -> {
                fortBuildRequested |= !fortBuildPressed;
                fortBuildPressed = true;
            }
            case KeyEvent.VK_G -> {
                forageToggleRequested |= !forageTogglePressed;
                forageTogglePressed = true;
            }
            case KeyEvent.VK_ESCAPE -> {
                buildCancelRequested |= !buildCancelPressed;
                buildCancelPressed = true;
            }
            default -> {
                // Do nothing
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W -> forwardPressed = false;
            case KeyEvent.VK_A -> leftPressed = false;
            case KeyEvent.VK_S -> backwardPressed = false;
            case KeyEvent.VK_D -> rightPressed = false;
            case KeyEvent.VK_SHIFT -> shiftPressed = false;
            case KeyEvent.VK_F11 -> f11Pressed = false;
            case KeyEvent.VK_R -> modeTogglePressed = false;
            case KeyEvent.VK_B -> bridgeBuildPressed = false;
            case KeyEvent.VK_F -> fortBuildPressed = false;
            case KeyEvent.VK_G -> forageTogglePressed = false;
            case KeyEvent.VK_ESCAPE -> buildCancelPressed = false;
            default -> {
                // Do nothing
            }
        }
    }

    public boolean isForwardPressed() {
        return forwardPressed;
    }

    public boolean isBackwardPressed() {
        return backwardPressed;
    }

    public boolean isLeftPressed() {
        return leftPressed;
    }

    public boolean isRightPressed() {
        return rightPressed;
    }

    public boolean isShiftPressed() {
        return shiftPressed;
    }

    public void setForwardPressed(boolean forwardPressed) {
        this.forwardPressed = forwardPressed;
    }

    public void setBackwardPressed(boolean backwardPressed) {
        this.backwardPressed = backwardPressed;
    }

    public void setLeftPressed(boolean leftPressed) {
        this.leftPressed = leftPressed;
    }

    public void setRightPressed(boolean rightPressed) {
        this.rightPressed = rightPressed;
    }

    public void setShiftPressed(boolean shiftPressed) {
        this.shiftPressed = shiftPressed;
    }

    public boolean isF11Pressed() {
        return this.f11Pressed;
    }

    public void setF11Pressed(boolean f11Pressed) {
        this.f11Pressed = f11Pressed;
    }

    public boolean isModeTogglePressed() {
        return this.modeTogglePressed;
    }

    public void setModeTogglePressed(boolean modeTogglePressed) {
        this.modeTogglePressed = modeTogglePressed;
    }

    public boolean isBridgeBuildPressed() {
        return this.bridgeBuildPressed;
    }

    public void setBridgeBuildPressed(boolean bridgeBuildPressed) {
        this.bridgeBuildPressed = bridgeBuildPressed;
    }

    public boolean isFortBuildPressed() {
        return this.fortBuildPressed;
    }

    public void setFortBuildPressed(boolean fortBuildPressed) {
        this.fortBuildPressed = fortBuildPressed;
    }

    public boolean isForageTogglePressed() {
        return this.forageTogglePressed;
    }

    public void setForageTogglePressed(boolean forageTogglePressed) {
        this.forageTogglePressed = forageTogglePressed;
    }

    public boolean isBuildCancelPressed() {
        return this.buildCancelPressed;
    }

    public void setBuildCancelPressed(boolean buildCancelPressed) {
        this.buildCancelPressed = buildCancelPressed;
    }

    public boolean isFullScreenToggleRequested() {
        return this.fullScreenToggleRequested;
    }

    public void setFullScreenToggleRequested(boolean fullScreenToggleRequested) {
        this.fullScreenToggleRequested = fullScreenToggleRequested;
    }

    public boolean isWeaponModeToggleRequested() {
        return this.weaponModeToggleRequested;
    }

    public void setWeaponModeToggleRequested(boolean weaponModeToggleRequested) {
        this.weaponModeToggleRequested = weaponModeToggleRequested;
    }

    public boolean isBridgeBuildRequested() {
        return this.bridgeBuildRequested;
    }

    public void setBridgeBuildRequested(boolean bridgeBuildRequested) {
        this.bridgeBuildRequested = bridgeBuildRequested;
    }

    public boolean isFortBuildRequested() {
        return this.fortBuildRequested;
    }

    public void setFortBuildRequested(boolean fortBuildRequested) {
        this.fortBuildRequested = fortBuildRequested;
    }

    public boolean isForageToggleRequested() {
        return this.forageToggleRequested;
    }

    public void setForageToggleRequested(boolean forageToggleRequested) {
        this.forageToggleRequested = forageToggleRequested;
    }

    public boolean isBuildCancelRequested() {
        return this.buildCancelRequested;
    }

    public void setBuildCancelRequested(boolean buildCancelRequested) {
        this.buildCancelRequested = buildCancelRequested;
    }

    public boolean consumeFullScreenToggleRequested() {
        boolean requested = fullScreenToggleRequested;
        fullScreenToggleRequested = false;
        return requested;
    }

    public boolean consumeWeaponModeToggleRequested() {
        boolean requested = weaponModeToggleRequested;
        weaponModeToggleRequested = false;
        return requested;
    }

    public boolean consumeBridgeBuildRequested() {
        boolean requested = bridgeBuildRequested;
        bridgeBuildRequested = false;
        return requested;
    }

    public boolean consumeFortBuildRequested() {
        boolean requested = fortBuildRequested;
        fortBuildRequested = false;
        return requested;
    }

    public boolean consumeForageToggleRequested() {
        boolean requested = forageToggleRequested;
        forageToggleRequested = false;
        return requested;
    }

    public boolean consumeBuildCancelRequested() {
        boolean requested = buildCancelRequested;
        buildCancelRequested = false;
        return requested;
    }

}
