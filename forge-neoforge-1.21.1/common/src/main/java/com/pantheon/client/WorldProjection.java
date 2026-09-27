package com.pantheon.client;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import net.minecraft.world.phys.Vec3;

/**
 * The camera position and view/projection matrices the world was last
 * rendered with, captured by {@code WorldProjectionCaptureMixin}, so a HUD
 * layer can find where a world position lands on screen. (Newer Minecraft
 * has {@code GameRenderer#projectPointToScreen} for this; 1.21.1 doesn't.)
 */
public final class WorldProjection {
	private static final Matrix4f VIEW = new Matrix4f();
	private static final Matrix4f PROJECTION = new Matrix4f();
	private static Vec3 cameraPos = Vec3.ZERO;
	private static boolean captured;

	private WorldProjection() {
	}

	public static void capture(final Vec3 position, final Matrix4f view, final Matrix4f projection) {
		cameraPos = position;
		VIEW.set(view);
		PROJECTION.set(projection);
		captured = true;
	}

	public static boolean isCaptured() {
		return captured;
	}

	public static Vec3 cameraPos() {
		return cameraPos;
	}

	/**
	 * Clip-space coordinates of {@code target}: divide x/y by w for
	 * normalized device coordinates. w is positive in front of the camera
	 * and not above zero behind it.
	 */
	public static Vector4f toClip(final Vec3 target) {
		Vector4f point = new Vector4f(
			(float) (target.x - cameraPos.x),
			(float) (target.y - cameraPos.y),
			(float) (target.z - cameraPos.z),
			1.0F);
		VIEW.transform(point);
		PROJECTION.transform(point);
		return point;
	}
}
