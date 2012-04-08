package aurelienribon.bodyeditor.canvas.rigidbody;

import aurelienribon.bodyeditor.Ctx;
import aurelienribon.bodyeditor.RigidBodiesManager;
import aurelienribon.bodyeditor.Settings;
import aurelienribon.bodyeditor.models.PolygonModel;
import aurelienribon.bodyeditor.models.RigidBodyModel;
import aurelienribon.utils.gdx.PrimitiveDrawer;
import aurelienribon.utils.gdx.ShapeUtils;
import aurelienribon.utils.gdx.TextureHelper;
import aurelienribon.utils.notifications.ChangeListener;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL10;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer10;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 *
 * @author Aurelien Ribon | http://www.aurelienribon.com/
 */
public class Canvas implements ApplicationListener {
	private static final float PX_PER_METER = 300;
	private static final Color COLOR_AREAS = new Color(0f, 0f, 0f, 0.7f);
	public static enum Modes {CREATION, EDITION, TEST}

	private final List<Body> ballsBodies = new ArrayList<Body>();
	private final List<Sprite> ballsSprites = new ArrayList<Sprite>();
	private final Random rand = new Random();
	private final Vector3 vec3 = new Vector3();

	private Modes mode = Modes.CREATION;

	private PrimitiveDrawer pDrawer;
	private CanvasDrawer drawer;
	private SpriteBatch sb;
	private BitmapFont font;
	private OrthographicCamera worldCamera;
	private OrthographicCamera screenCamera;

	private Texture backgroundLightTexture;
	private Texture backgroundDarkTexture;
	private Texture ballTexture;
	private Sprite bodySprite;
	private Sprite btnCreationSprite;
	private Sprite btnEditionSprite;
	private Sprite btnTestSprite;

	private World world;

	// -------------------------------------------------------------------------
	// Gdx API
	// -------------------------------------------------------------------------

	@Override
	public void create() {
		pDrawer = new PrimitiveDrawer(new ImmediateModeRenderer10());
		drawer = new CanvasDrawer(this, pDrawer);
		sb = new SpriteBatch();
		font = new BitmapFont();

		float w = Gdx.graphics.getWidth();
		float h = Gdx.graphics.getHeight();

		worldCamera = new OrthographicCamera(2, 2/w/h);
		worldCamera.position.set(0.5f, 0.5f/w/h, 0);
		worldCamera.update();
		screenCamera = new OrthographicCamera(w, h);
		screenCamera.position.set(w/2, h/2, 0);
		screenCamera.update();

		backgroundLightTexture = new Texture(Gdx.files.classpath("aurelienribon/bodyeditor/ui/gfx/transparent-light.png"));
		backgroundDarkTexture = new Texture(Gdx.files.classpath("aurelienribon/bodyeditor/ui/gfx/transparent-dark.png"));
		ballTexture = new Texture(Gdx.files.classpath("aurelienribon/bodyeditor/ui/gfx/ball.png"));

		backgroundLightTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
		backgroundDarkTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

		TextureAtlas buttonsAtlas = new TextureAtlas(Gdx.files.classpath("aurelienribon/bodyeditor/ui/gfx/pack-buttons"));

		btnCreationSprite = buttonsAtlas.createSprite("btn_creation");
		btnEditionSprite = buttonsAtlas.createSprite("btn_edition");
		btnTestSprite = buttonsAtlas.createSprite("btn_test");
		btnCreationSprite.setPosition(119, 3);
		btnEditionSprite.setPosition(119, 3);
		btnTestSprite.setPosition(119, 3);
		btnCreationSprite.setColor(1, 1, 1, 1);
		btnEditionSprite.setColor(1, 1, 1, 0);
		btnTestSprite.setColor(1, 1, 1, 0);

		InputMultiplexer im = new InputMultiplexer();
		im.addProcessor(new PanZoomInputProcessor(this));
		im.addProcessor(new CollisionTestInputProcessor(this));
		im.addProcessor(new ShapeCreationInputProcessor(this));
		im.addProcessor(new ShapeEditionInputProcessor(this));
		im.addProcessor(new ModeInputProcessor(this));
		Gdx.input.setInputProcessor(im);

		world = new World(new Vector2(0, 0), true);

		Ctx.bodies.addChangeListener(new ChangeListener() {
			@Override public void propertyChanged(Object source, String propertyName) {
				if (propertyName.equals(RigidBodiesManager.PROP_SELECTION)) {
					clearWorld();
					bodySprite = null;

					float ratio = (float)Gdx.graphics.getWidth() / Gdx.graphics.getHeight();
					worldCamera.position.set(0.5f, 0.5f/ratio, 0);
					worldCamera.update();

					RigidBodyModel model = Ctx.bodies.getSelectedModel();
					if (model == null) return;

					createBody();

					TextureRegion tex = TextureHelper.getPOTTexture(model.getImagePath());
					if (tex == null) return;

					bodySprite = new Sprite(tex);
					bodySprite.setPosition(0, 0);
					bodySprite.setColor(1, 1, 1, 0.5f);

					float bodySpriteRatio = bodySprite.getWidth() / bodySprite.getHeight();
					if (bodySpriteRatio >= 1) {
						bodySprite.setSize(1f, 1f/bodySpriteRatio);
					} else {
						bodySprite.setSize(1f*bodySpriteRatio, 1f);
					}
				}
			}
		});

		CanvasEvents.addListener(new CanvasEvents.Listener() {
			@Override public void recreateWorldRequested() {
				clearWorld();
				createBody();
			}
		});
	}

	@Override
	public void render() {
		world.step(Gdx.graphics.getDeltaTime(), 10, 10);

		GL10 gl = Gdx.gl10;
		gl.glClearColor(1, 1, 1, 1);
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
		gl.glEnable(GL10.GL_BLEND);
		gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);

		int w = Gdx.graphics.getWidth();
		int h = Gdx.graphics.getHeight();

		sb.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
		sb.begin();
		sb.disableBlending();
		if (Settings.isBackgroundLight) {
			float tw = backgroundLightTexture.getWidth();
			float th = backgroundLightTexture.getHeight();
			sb.draw(backgroundLightTexture, 0f, 0f, w, h, 0f, 0f, w/tw, h/th);
		} else {
			float tw = backgroundDarkTexture.getWidth();
			float th = backgroundDarkTexture.getHeight();
			sb.draw(backgroundDarkTexture, 0f, 0f, w, h, 0f, 0f, w/tw, h/th);
		}
		sb.enableBlending();
		sb.end();

		sb.setProjectionMatrix(worldCamera.combined);
		sb.begin();
		if (bodySprite != null && Settings.isImageDrawn) bodySprite.draw(sb);
		for (int i=0; i<ballsSprites.size(); i++) {
			Sprite sp = ballsSprites.get(i);
			Vector2 pos = ballsBodies.get(i).getWorldCenter().mul(PX_PER_METER).sub(sp.getWidth()/2, sp.getHeight()/2);
			float angleDeg = ballsBodies.get(i).getAngle() * MathUtils.radiansToDegrees;
			sp.setPosition(pos.x, pos.y);
			sp.setRotation(angleDeg);
			sp.draw(sb);
		}
		sb.end();

		worldCamera.apply(gl);
		drawer.draw(worldCamera, bodySprite);

		screenCamera.apply(gl);
		pDrawer.fillRect(0, 0, 220, 60, COLOR_AREAS);

		sb.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
		sb.begin();
		font.setColor(Color.WHITE);
		font.draw(sb, String.format(Locale.US, "Zoom: %.0f %%", 100f / worldCamera.zoom), 10, 45);
		font.draw(sb, "Fps: " + Gdx.graphics.getFramesPerSecond(), 10, 25);
		btnTestSprite.draw(sb);
		btnEditionSprite.draw(sb);
		btnCreationSprite.draw(sb);
		sb.end();
	}

	@Override
	public void resize(int width, int height) {
		GL10 gl = Gdx.gl10;
		gl.glViewport(0, 0, width, height);
		worldCamera.viewportWidth = 2;
		worldCamera.viewportHeight = 2 / ((float)width / height);
		worldCamera.update();
	}

	@Override public void resume() {}
	@Override public void pause() {}
	@Override public void dispose() {}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	public Vector2 screenToWorld(int x, int y) {
		worldCamera.unproject(vec3.set(x, y, 0));
		return new Vector2(vec3.x, vec3.y);
	}

	public Vector2 alignedScreenToWorld(int x, int y) {
		Vector2 p = screenToWorld(x, y);
		if (Settings.isSnapToGridEnabled) {
			float gap = Settings.gridGap;
			p.x = Math.round(p.x / gap) * gap;
			p.y = Math.round(p.y / gap) * gap;
		}
		return p;
	}

	public OrthographicCamera getCamera() {
		return worldCamera;
	}

	public Modes getMode() {
		return mode;
	}

	public void setNextMode() {
		CanvasObjects.nextPoint = null;

		mode = mode == Modes.CREATION
			? Modes.EDITION : mode == Modes.EDITION
			? Modes.TEST : Modes.CREATION;

		switch (mode) {
			case CREATION:
				btnCreationSprite.setColor(1, 1, 1, 1);
				btnEditionSprite.setColor(1, 1, 1, 0);
				btnTestSprite.setColor(1, 1, 1, 0);
				break;
			case EDITION:
				btnCreationSprite.setColor(1, 1, 1, 0);
				btnEditionSprite.setColor(1, 1, 1, 1);
				btnTestSprite.setColor(1, 1, 1, 0);
				break;
			case TEST:
				btnCreationSprite.setColor(1, 1, 1, 0);
				btnEditionSprite.setColor(1, 1, 1, 0);
				btnTestSprite.setColor(1, 1, 1, 1);
				break;
		}
	}

	public void createBody() {
		RigidBodyModel model = Ctx.bodies.getSelectedModel();
		if (model == null || model.getPolygons().isEmpty()) return;

		Body body = world.createBody(new BodyDef());

		for (PolygonModel polygon : model.getPolygons()) {
			Vector2[] resizedPolygon = new Vector2[polygon.getVertices().size()];
			for (int i=0; i<polygon.getVertices().size(); i++)
				resizedPolygon[i] = new Vector2(polygon.getVertices().get(i)).mul(1f / PX_PER_METER);

			if (ShapeUtils.getPolygonArea(resizedPolygon) < 0.01f)
				continue;

			PolygonShape shape = new PolygonShape();
			shape.set(resizedPolygon);

			FixtureDef fd = new FixtureDef();
			fd.density = 1f;
			fd.friction = 0.8f;
			fd.restitution = 0.2f;
			fd.shape = shape;

			body.createFixture(fd);
			shape.dispose();
		}
	}

	public void fireBall(Vector2 orig, Vector2 force) {
		float radius = rand.nextFloat() * 0.02f + 0.02f;

		BodyDef bd = new BodyDef();
		bd.angularDamping = 0.5f;
		bd.linearDamping = 0.5f;
		bd.type = BodyType.DynamicBody;
		bd.position.set(orig).mul(1 / PX_PER_METER);
		bd.angle = rand.nextFloat() * MathUtils.PI;
		Body b = world.createBody(bd);
		b.applyLinearImpulse(force.mul(2 / PX_PER_METER), orig);
		ballsBodies.add(b);

		CircleShape shape = new CircleShape();
		shape.setRadius(radius / PX_PER_METER);
		b.createFixture(shape, 1f);

		Sprite sp = new Sprite(ballTexture);
		sp.setSize(radius*2, radius*2);
		sp.setOrigin(sp.getWidth()/2, sp.getHeight()/2);
		ballsSprites.add(sp);
	}

	// -------------------------------------------------------------------------
	// Internals
	// -------------------------------------------------------------------------

	private void clearWorld() {
		ballsBodies.clear();
		ballsSprites.clear();
		Iterator<Body> bodies = world.getBodies();
		while (bodies.hasNext())
			world.destroyBody(bodies.next());
	}
}
