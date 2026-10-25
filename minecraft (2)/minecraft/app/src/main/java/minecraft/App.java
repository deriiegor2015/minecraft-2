package minecraft;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBImage.*;

public class App {
    
    private long window;
    
    private int grassTexture;
    private int dirtTexture;
    private int stoneTexture;
    private int logTexture;
    private int leavesTexture;
    private int goldOreTexture;
    private int coalOreTexture;
    private int ironOreTexture;
    private int furnaceTexture;
    private int craftingTableTexture;
    
    private enum GameState { MAIN_MENU, PLAYING }
    private GameState currentState = GameState.MAIN_MENU;

    private float panoramaAngle = 0.0f;

    private static final int CHUNK_SIZE = 16;
    private static final int RENDER_DISTANCE = 3;
    private static final long WORLD_SEED = 1337228L;
    private final Map<String, int[][]> chunks = new HashMap<>();
    
    private final Map<String, Integer> customHeights = new HashMap<>();
    private final Map<String, Integer> blockMaterials = new HashMap<>();
    private final Map<String, Integer> treeStructures = new HashMap<>();
    private int selectedBlockType = 1;

    private boolean hasCraftingTable = false;

    private float worldTime = 0.3f;

    private float playerX = 8.0f;
    private float playerY = 18.0f;
    private float playerZ = 8.0f;
    private float velocityY = 0.0f;
    private boolean isGrounded = false;

    private float yaw = -90.0f;
    private float pitch = -20.0f; 
    private double lastMouseX = 960.0;
    private double lastMouseY = 540.0;
    private boolean firstMouse = true;

    private int windowWidth = 1920;
    private int windowHeight = 1080;

    private int playerTextureID;

    private boolean isMapOverview = false;
    private boolean isInventoryOpen = false;

    public void run() {
        init();
        loadWorld();
        loop();
        saveWorld();
        glfwTerminate();
    }

    private int loadTexture(String filename) {
        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        IntBuffer width = BufferUtils.createIntBuffer(1);
        IntBuffer height = BufferUtils.createIntBuffer(1);
        IntBuffer channels = BufferUtils.createIntBuffer(1);

        ByteBuffer image = null;
        try {
            try (InputStream in = App.class.getResourceAsStream("/textures/" + filename)) {
                if (in != null) {
                    byte[] fileBytes = in.readAllBytes();
                    ByteBuffer buffer = BufferUtils.createByteBuffer(fileBytes.length);
                    buffer.put(fileBytes);
                    buffer.flip();
                    image = stbi_load_from_memory(buffer, width, height, channels, 4);
                }
            }

            if (image == null) {
                String[] possiblePaths = {
                    "textures/" + filename,
                    "src/main/resources/textures/" + filename,
                    "bin/main/textures/" + filename,
                    "app/bin/main/textures/" + filename,
                    "D:/minecraft/textures/" + filename,
                    "D:/minecraft/minecraft/textures/" + filename,
                    "D:/minecraft/minecraft/app/bin/main/textures/" + filename,
                    "D:/minecraft/minecraft/src/main/resources/textures/" + filename
                };

                for (String path : possiblePaths) {
                    File file = new File(path);
                    if (file.exists()) {
                        byte[] fileBytes = Files.readAllBytes(file.toPath());
                        ByteBuffer buffer = BufferUtils.createByteBuffer(fileBytes.length);
                        buffer.put(fileBytes);
                        buffer.flip();
                        image = stbi_load_from_memory(buffer, width, height, channels, 4);
                        if (image != null) break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (image != null) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(0), height.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
            stbi_image_free(image);
        } else {
            throw new RuntimeException("Не вдалося завантажити текстуру: " + filename);
        }

        return textureId;
    }

    private void init() {
        if (!glfwInit()) {
            throw new IllegalStateException("Не вдалося ініціалізувати GLFW");
        }

        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);

        long primaryMonitor = glfwGetPrimaryMonitor();
        org.lwjgl.glfw.GLFWVidMode vidMode = glfwGetVideoMode(primaryMonitor);
        if (vidMode != null) {
            windowWidth = vidMode.width();
            windowHeight = vidMode.height();
        }

        window = glfwCreateWindow(windowWidth, windowHeight, "Java 3D Minecraft", primaryMonitor, 0);
        if (window == 0) {
            throw new RuntimeException("Не вдалося створити повноеранне вікно");
        }

        glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
            windowWidth = width;
            windowHeight = height;
            glViewport(0, 0, width, height);
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (currentState == GameState.MAIN_MENU) {
                if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                    currentState = GameState.PLAYING;
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    firstMouse = true;
                }
            } else if (currentState == GameState.PLAYING && action == GLFW_PRESS) {
                if (isInventoryOpen) {
                    if (button == GLFW_MOUSE_BUTTON_LEFT) {
                        hasCraftingTable = true;
                        System.out.println("[ІНФО] Верстак успішно скрафчено в інвентарі!");
                    }
                } else {
                    if (button == GLFW_MOUSE_BUTTON_LEFT) {
                        breakBlock();
                    } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                        placeBlock();
                    }
                }
            }
        });

        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (currentState == GameState.PLAYING && !isMapOverview && !isInventoryOpen) {
                if (firstMouse) {
                    lastMouseX = xpos;
                    lastMouseY = ypos;
                    firstMouse = false;
                }

                double xoffset = xpos - lastMouseX;
                double yoffset = lastMouseY - ypos;
                lastMouseX = xpos;
                lastMouseY = ypos;

                float sensitivity = 0.1f;
                yaw += (float) (xoffset * sensitivity);
                pitch += (float) (yoffset * sensitivity);

                if (pitch > 89.0f) pitch = 89.0f;
                if (pitch < -89.0f) pitch = -89.0f;
            }
        });

        glfwMakeContextCurrent(window);
        glfwSwapInterval(0);
        glfwShowWindow(window);
        GL.createCapabilities();
        
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glEnable(GL_TEXTURE_2D);

        playerTextureID = loadTexture("images.png");
        grassTexture = loadTexture("Grass_block.png");
        dirtTexture = loadTexture("Dirt_29_JE2_BE2.png");
        stoneTexture = loadTexture("Stone_29_JE5_BE3.png");
        logTexture = loadTexture("Oak_Log_29_JE4_BE2.png");
        leavesTexture = loadTexture("Acacia_Leaves__JE3.png");
        goldOreTexture = loadTexture("Gold_Ore_29_JE7_BE4.png");
        coalOreTexture = loadTexture("Coal_Ore_29_JE5_BE4.png");
        ironOreTexture = loadTexture("Iron_Ore_29_JE6_BE4.png");
        furnaceTexture = loadTexture("Furnace_29_JE1_BE11.png");
        craftingTableTexture = loadTexture("Crafting_Table_29_JE2_BE2.png");
    }

    private int[][] generateChunkData(int chunkX, int chunkZ) {
        int[][] heights = new int[CHUNK_SIZE][CHUNK_SIZE];
        Random random = new Random(WORLD_SEED + (long) chunkX * 3418731287L ^ (long) chunkZ * 1328979875L);
        
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int worldX = chunkX * CHUNK_SIZE + x;
                int worldZ = chunkZ * CHUNK_SIZE + z;
                
                float hillNoise1 = (float) (Math.sin(worldX * 0.04f) * Math.cos(worldZ * 0.04f) * 6.0f);
                float hillNoise2 = (float) (Math.sin(worldX * 0.15f) * Math.cos(worldZ * 0.15f) * 2.5f);
                int h = (int) (hillNoise1 + hillNoise2 + 14);
                heights[x][z] = h;

                if (random.nextInt(100) < 1) { 
                    generateTree(worldX, h + 1, worldZ);
                }
            }
        }
        return heights;
    }

    private void generateTree(int x, int baseY, int z) {
        for (int ty = 0; ty < 4; ty++) {
            treeStructures.put(x + "," + (baseY + ty) + "," + z, 3);
        }
        int leafY = baseY + 4;
        for (int lx = -1; lx <= 1; lx++) {
            for (int lz = -1; lz <= 1; lz++) {
                for (int ly = 0; ly <= 1; ly++) {
                    treeStructures.put((x + lx) + "," + (leafY + ly) + "," + (z + lz), 4);
                }
            }
        }
    }

    private int[][] getChunk(int chunkX, int chunkZ) {
        String key = chunkX + "," + chunkZ;
        if (!chunks.containsKey(key)) {
            chunks.put(key, generateChunkData(chunkX, chunkZ));
        }
        return chunks.get(key);
    }

    private int getBlockHeight(int wx, int wz) {
        int cx = Math.floorDiv(wx, CHUNK_SIZE);
        int cz = Math.floorDiv(wz, CHUNK_SIZE);
        int[][] chunk = getChunk(cx, cz);
        int lx = wx - cx * CHUNK_SIZE;
        int lz = wz - cz * CHUNK_SIZE;
        if (lx >= 0 && lx < CHUNK_SIZE && lz >= 0 && lz < CHUNK_SIZE) {
            String bKey = wx + "," + wz;
            if (customHeights.containsKey(bKey)) {
                return customHeights.get(bKey);
            }
            return chunk[lx][lz];
        }
        return 14;
    }

    private int getBlockMaterial(int wx, int wz) {
        String bKey = wx + "," + wz;
        return blockMaterials.getOrDefault(bKey, 1);
    }

    private boolean isCave(int wx, int y, int wz) {
        int surfaceH = getBlockHeight(wx, wz);
        if (y < surfaceH - 2 && y > 2) {
            float caveNoise = (float) (Math.sin(wx * 0.3f) * Math.cos(wz * 0.3f) * Math.sin(y * 0.4f));
            return caveNoise > 0.65f;
        }
        return false;
    }

    private int getUndergroundBlock(int wx, int y, int wz) {
        Random r = new Random(WORLD_SEED ^ ((long)wx * 73856093L ^ (long)y * 19349663L ^ (long)wz * 83492791L));
        int val = r.nextInt(1000);
        if (val < 15) return 5;
        if (val < 45) return 7;
        if (val < 95) return 6;
        return 2;
    }

    private void breakBlock() {
        int targetX = (int) Math.floor(playerX + Math.sin(Math.toRadians(yaw)) * 1.5f);
        int targetZ = (int) Math.floor(playerZ - Math.cos(Math.toRadians(yaw)) * 1.5f);
        int currentH = getBlockHeight(targetX, targetZ);
        if (currentH >= 0) {
            String bKey = targetX + "," + targetZ;
            int mat = blockMaterials.getOrDefault(bKey, (currentH < 8 ? 2 : 1));
            
            customHeights.put(bKey, currentH - 1);
            selectedBlockType = (mat >= 1 && mat <= 9) ? mat : 1;
        }
    }

    private void placeBlock() {
        if ((selectedBlockType == 8 || selectedBlockType == 9) && !hasCraftingTable) {
            System.out.println("[ПОПЕРЕДЖЕННЯ] Спочатку скрафтіть верстак в інвентарі (натисніть H, потім клікніть)!");
            return;
        }

        int targetX = (int) Math.floor(playerX + Math.sin(Math.toRadians(yaw)) * 1.5f);
        int targetZ = (int) Math.floor(playerZ - Math.cos(Math.toRadians(yaw)) * 1.5f);
        int currentH = getBlockHeight(targetX, targetZ);
        int newH = currentH + 1;
        customHeights.put(targetX + "," + targetZ, newH);
        blockMaterials.put(targetX + "," + targetZ, selectedBlockType);
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            worldTime += 0.0001f;
            if (worldTime > 1.0f) worldTime = 0.0f;

            float sunAngle = (float) Math.sin(worldTime * 2.0f * Math.PI);
            float skyBrightness = Math.max(0.2f, (sunAngle + 1.0f) / 2.0f);
            glClearColor(0.52f * skyBrightness, 
                         0.75f * skyBrightness, 
                         1.0f * skyBrightness, 
                         1.0f);
                         
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            if (currentState == GameState.MAIN_MENU) {
                updateMainMenu();
                renderMainMenu();
            } else {
                handleInput();
                renderGameWorld();
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void handleInput() {
        if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) {
            glfwSetWindowShouldClose(window, true);
        }

        if (glfwGetKey(window, GLFW_KEY_J) == GLFW_PRESS) {
            saveWorld();
        }

        if (glfwGetKey(window, GLFW_KEY_H) == GLFW_PRESS) {
            isInventoryOpen = !isInventoryOpen;
            if (isInventoryOpen || isMapOverview) {
                glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            } else {
                glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                firstMouse = true;
            }
            try { Thread.sleep(200); } catch (InterruptedException e) {}
        }

        if (isInventoryOpen && glfwGetKey(window, GLFW_KEY_C) == GLFW_PRESS) {
            hasCraftingTable = true;
            System.out.println("[ІНФО] Верстак успішно скрафчено!");
            try { Thread.sleep(200); } catch (InterruptedException e) {}
        }

        if (glfwGetKey(window, GLFW_KEY_M) == GLFW_PRESS) {
            isMapOverview = !isMapOverview;
            if (isMapOverview || isInventoryOpen) {
                glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            } else {
                glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                firstMouse = true;
            }
            try { Thread.sleep(200); } catch (InterruptedException e) {}
        }

        if (isInventoryOpen) return;

        if (glfwGetKey(window, GLFW_KEY_1) == GLFW_PRESS) selectedBlockType = 1;
        if (glfwGetKey(window, GLFW_KEY_2) == GLFW_PRESS) selectedBlockType = 2;
        if (glfwGetKey(window, GLFW_KEY_3) == GLFW_PRESS) selectedBlockType = 3;
        if (glfwGetKey(window, GLFW_KEY_4) == GLFW_PRESS) selectedBlockType = 7;
        if (glfwGetKey(window, GLFW_KEY_5) == GLFW_PRESS) selectedBlockType = 8;
        if (glfwGetKey(window, GLFW_KEY_6) == GLFW_PRESS) selectedBlockType = 9;

        float speed = 0.12f;
        float rad = (float) Math.toRadians(yaw);
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            playerX += (float) Math.sin(rad) * speed;
            playerZ -= (float) Math.cos(rad) * speed;
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            playerX -= (float) Math.sin(rad) * speed;
            playerZ += (float) Math.cos(rad) * speed;
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            playerX -= (float) Math.cos(rad) * speed;
            playerZ -= (float) Math.sin(rad) * speed;
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            playerX += (float) Math.cos(rad) * speed;
            playerZ += (float) Math.sin(rad) * speed;
        }

        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS && isGrounded && !isMapOverview) {
            velocityY = 0.15f;
            isGrounded = false;
        }

        if (!isMapOverview) {
            velocityY -= 0.008f;
            playerY += velocityY;

            int groundH = getBlockHeight((int) Math.floor(playerX), (int) Math.floor(playerZ));
            float groundHeight = groundH + 1.8f;
            if (playerY <= groundHeight) {
                playerY = groundHeight;
                velocityY = 0.0f;
                isGrounded = true;
            }
        } else {
            if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) playerY += 0.2f;
            if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) playerY -= 0.2f;
        }
    }

    private void updateMainMenu() {
        panoramaAngle += 0.4f;
        if (panoramaAngle >= 360.0f) panoramaAngle = 0.0f;
    }

    private void renderMainMenu() {
        setupPerspective();
        glRotatef(panoramaAngle, 0.0f, 1.0f, 0.0f);
        glTranslatef(-8.0f, -10.0f, -8.0f);
        drawChunksAround(0, 0);

        setupOrthographic();
        float btnX = -0.42f, btnY = -0.08f, btnWidth = 0.84f, btnHeight = 0.16f;
        drawRect2D(btnX, btnY, btnWidth, btnHeight, 0.15f, 0.15f, 0.15f, 0.9f);
        drawRectOutline2D(btnX, btnY, btnWidth, btnHeight, 0.8f, 0.8f, 0.8f);
        drawButtonText(-0.36f, -0.02f);
    }

    private void renderGameWorld() {
        setupPerspective();

        if (isMapOverview) {
            glDisable(GL_CULL_FACE);
            glRotatef(90.0f, 1.0f, 0.0f, 0.0f);
            glTranslatef(-playerX, -playerZ, -playerY - 25.0f);
        } else {
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
            glRotatef(pitch, 1.0f, 0.0f, 0.0f);
            glRotatef(yaw + 90.0f, 0.0f, 1.0f, 0.0f);
            glTranslatef(-playerX, -playerY, -playerZ);
        }

        int pChunkX = (int) Math.floor(playerX / CHUNK_SIZE);
        int pChunkZ = (int) Math.floor(playerZ / CHUNK_SIZE);
        drawChunksAround(pChunkX, pChunkZ);
        
        renderCrosshair();
        renderHotbarUI();

        if (isInventoryOpen) {
            renderInventory();
        }
    }

    private void renderInventory() {
        setupOrthographic();
        glDisable(GL_DEPTH_TEST);

        drawRect2D(-0.5f, -0.6f, 1.0f, 1.2f, 0.15f, 0.15f, 0.15f, 0.9f);
        drawRectOutline2D(-0.5f, -0.6f, 1.0f, 1.2f, 0.8f, 0.8f, 0.8f);

        if (hasCraftingTable) {
            drawRect2D(-0.2f, 0.1f, 0.4f, 0.2f, 0.2f, 0.6f, 0.2f, 0.9f);
        } else {
            drawRect2D(-0.2f, 0.1f, 0.4f, 0.2f, 0.6f, 0.2f, 0.2f, 0.9f);
        }
        drawRectOutline2D(-0.2f, 0.1f, 0.4f, 0.2f, 1.0f, 1.0f, 1.0f);

        glEnable(GL_DEPTH_TEST);
    }

    private void drawChunksAround(int centerCX, int centerCZ) {
        glColor3f(1.0f, 1.0f, 1.0f);

        for (int cx = centerCX - RENDER_DISTANCE; cx <= centerCX + RENDER_DISTANCE; cx++) {
            for (int cz = centerCZ - RENDER_DISTANCE; cz <= centerCZ + RENDER_DISTANCE; cz++) {
                int[][] heights = getChunk(cx, cz);
                
                int startX = cx * CHUNK_SIZE;
                int startZ = cz * CHUNK_SIZE;

                for (int x = 0; x < CHUNK_SIZE; x++) {
                    for (int z = 0; z < CHUNK_SIZE; z++) {
                        int wx = startX + x;
                        int wz = startZ + z;
                        int h = getBlockHeight(wx, wz);
                        int mat = getBlockMaterial(wx, wz);

                        for (int y = 0; y <= h; y++) {
                            if (isCave(wx, y, wz)) {
                                continue;
                            }

                            int textureToDraw;

                            if (mat == 2) {
                                textureToDraw = stoneTexture;
                            } else if (mat == 3) {
                                textureToDraw = logTexture;
                            } else if (mat == 5) {
                                textureToDraw = goldOreTexture;
                            } else if (mat == 6) {
                                textureToDraw = coalOreTexture;
                            } else if (mat == 7) {
                                textureToDraw = ironOreTexture;
                            } else if (mat == 8) {
                                textureToDraw = furnaceTexture;
                            } else if (mat == 9) {
                                textureToDraw = craftingTableTexture;
                            } else {
                                if (y == h) {
                                    textureToDraw = grassTexture;
                                } else if (y >= h - 3) {
                                    textureToDraw = dirtTexture;
                                } else {
                                    int undergroundMat = getUndergroundBlock(wx, y, wz);
                                    if (undergroundMat == 5) textureToDraw = goldOreTexture;
                                    else if (undergroundMat == 6) textureToDraw = coalOreTexture;
                                    else if (undergroundMat == 7) textureToDraw = ironOreTexture;
                                    else textureToDraw = stoneTexture;
                                }
                            }

                            drawCube(wx, y, wz, textureToDraw);
                        }
                    }
                }
            }
        }

        for (Map.Entry<String, Integer> entry : treeStructures.entrySet()) {
            String[] parts = entry.getKey().split(",");
            int tx = Integer.parseInt(parts[0]);
            int ty = Integer.parseInt(parts[1]);
            int tz = Integer.parseInt(parts[2]);
            int tMat = entry.getValue();

            int textureToDraw = (tMat == 3) ? logTexture : leavesTexture;
            drawCube(tx, ty, tz, textureToDraw);
        }
    }

    private void drawCube(float x, float y, float z, int textureID) {
        glBindTexture(GL_TEXTURE_2D, textureID);
        float u0 = 0.0f, v0 = 0.0f, u1 = 1.0f, v1 = 1.0f;

        glBegin(GL_QUADS);
        // Top
        glTexCoord2f(u0, v1); glVertex3f(x, y + 1, z + 1);
        glTexCoord2f(u1, v1); glVertex3f(x + 1, y + 1, z + 1);
        glTexCoord2f(u1, v0); glVertex3f(x + 1, y + 1, z);
        glTexCoord2f(u0, v0); glVertex3f(x, y + 1, z);
        // Bottom
        glTexCoord2f(u0, v0); glVertex3f(x, y, z);
        glTexCoord2f(u1, v0); glVertex3f(x + 1, y, z);
        glTexCoord2f(u1, v1); glVertex3f(x + 1, y, z + 1);
        glTexCoord2f(u0, v1); glVertex3f(x, y, z + 1);
        // Front
        glTexCoord2f(u0, v1); glVertex3f(x, y, z + 1);
        glTexCoord2f(u1, v1); glVertex3f(x + 1, y, z + 1);
        glTexCoord2f(u1, v0); glVertex3f(x + 1, y + 1, z + 1);
        glTexCoord2f(u0, v0); glVertex3f(x, y + 1, z + 1);
        // Back
        glTexCoord2f(u1, v1); glVertex3f(x + 1, y, z);
        glTexCoord2f(u0, v1); glVertex3f(x, y, z);
        glTexCoord2f(u0, v0); glVertex3f(x, y + 1, z);
        glTexCoord2f(u1, v0); glVertex3f(x + 1, y + 1, z);
        // Left
        glTexCoord2f(u0, v1); glVertex3f(x, y, z);
        glTexCoord2f(u1, v1); glVertex3f(x, y, z + 1);
        glTexCoord2f(u1, v0); glVertex3f(x + 1, y + 1, z + 1);
        glTexCoord2f(u0, v0); glVertex3f(x + 1, y + 1, z);
        // Right
        glTexCoord2f(u0, v1); glVertex3f(x + 1, y, z + 1);
        glTexCoord2f(u1, v1); glVertex3f(x + 1, y, z);
        glTexCoord2f(u1, v0); glVertex3f(x + 1, y + 1, z);
        glTexCoord2f(u0, v0); glVertex3f(x + 1, y + 1, z + 1);
        glEnd();
    }

    private void renderHotbarUI() {
        setupOrthographic();
        glDisable(GL_DEPTH_TEST);

        float startX = -0.6f;
        float slotSize = 0.15f;
        float spacing = 0.03f;

        for (int i = 1; i <= 6; i++) {
            float x = startX + (i - 1) * (slotSize + spacing);
            float y = -0.9f;

            boolean isSelected = (selectedBlockType == i) || 
                                 (i == 4 && selectedBlockType == 7) || 
                                 (i == 5 && selectedBlockType == 8) || 
                                 (i == 6 && selectedBlockType == 9);

            if (isSelected) {
                drawRect2D(x, y, slotSize, slotSize, 0.4f, 0.4f, 0.4f, 0.8f);
                drawRectOutline2D(x, y, slotSize, slotSize, 1.0f, 1.0f, 1.0f);
            } else {
                drawRect2D(x, y, slotSize, slotSize, 0.2f, 0.2f, 0.2f, 0.6f);
                drawRectOutline2D(x, y, slotSize, slotSize, 0.5f, 0.5f, 0.5f);
            }
        }

        glEnable(GL_DEPTH_TEST);
    }

    private void renderCrosshair() {
        setupOrthographic();
        glDisable(GL_DEPTH_TEST);
        glColor3f(1.0f, 1.0f, 1.0f);
        glLineWidth(2.0f);
        glBegin(GL_LINES);
        glVertex2f(-0.02f, 0.0f); glVertex2f(0.02f, 0.0f);
        glVertex2f(0.0f, -0.03f); glVertex2f(0.0f, 0.03f);
        glEnd();
        glEnable(GL_DEPTH_TEST);
    }

    private void saveWorld() {
        try (PrintWriter writer = new PrintWriter(new FileWriter("world.dat"))) {
            writer.println(customHeights.size());
            for (Map.Entry<String, Integer> entry : customHeights.entrySet()) {
                writer.println(entry.getKey() + ":" + entry.getValue());
            }
            writer.println(blockMaterials.size());
            for (Map.Entry<String, Integer> entry : blockMaterials.entrySet()) {
                writer.println(entry.getKey() + ":" + entry.getValue());
            }
            writer.println(playerX + "," + playerY + "," + playerZ);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadWorld() {
        File file = new File("world.dat");
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            int hSize = Integer.parseInt(reader.readLine());
            for (int i = 0; i < hSize; i++) {
                String[] parts = reader.readLine().split(":");
                customHeights.put(parts[0], Integer.parseInt(parts[1]));
            }
            int mSize = Integer.parseInt(reader.readLine());
            for (int i = 0; i < mSize; i++) {
                String[] parts = reader.readLine().split(":");
                blockMaterials.put(parts[0], Integer.parseInt(parts[1]));
            }
            String[] pos = reader.readLine().split(",");
            playerX = Float.parseFloat(pos[0]);
            playerY = Float.parseFloat(pos[1]);
            playerZ = Float.parseFloat(pos[2]);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupPerspective() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float aspect = (float) windowWidth / (float) windowHeight;
        float fov = 70.0f;
        float zNear = 0.1f;
        float zFar = 100.0f;
        float ymax = zNear * (float) Math.tan(Math.toRadians(fov / 2.0));
        float xmax = ymax * aspect;
        glFrustum(-xmax, xmax, -ymax, ymax, zNear, zFar);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private void setupOrthographic() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(-1.0, 1.0, -1.0, 1.0, -1.0, 1.0);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glDisable(GL_CULL_FACE);
    }

    private void drawRect2D(float x, float y, float w, float h, float r, float g, float b, float a) {
        glDisable(GL_DEPTH_TEST);
        glColor4f(r, g, b, a);
        glBegin(GL_QUADS);
        glVertex2f(x, y); glVertex2f(x + w, y); glVertex2f(x + w, y + h); glVertex2f(x, y + h);
        glEnd();
        glEnable(GL_DEPTH_TEST);
    }

    private void drawRectOutline2D(float x, float y, float w, float h, float r, float g, float b) {
        glDisable(GL_DEPTH_TEST);
        glColor3f(r, g, b);
        glLineWidth(3.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y); glVertex2f(x + w, y); glVertex2f(x + w, y + h); glVertex2f(x, y + h);
        glEnd();
        glEnable(GL_DEPTH_TEST);
    }

    private void drawButtonText(float x, float y) {
        glDisable(GL_DEPTH_TEST);
        glColor3f(1.0f, 1.0f, 1.0f);
        glLineWidth(2.5f);
        glBegin(GL_LINES);

        float s = 0.032f;
        float sp = 0.045f;
        float cx = x;

        // S
        glVertex2f(cx, y + s); glVertex2f(cx + s, y + s);
        glVertex2f(cx, y + s / 2); glVertex2f(cx, y + s);
        glVertex2f(cx, y + s / 2); glVertex2f(cx + s, y + s / 2);
        glVertex2f(cx + s, y); glVertex2f(cx + s, y + s / 2);
        glVertex2f(cx, y); glVertex2f(cx + s, y);
        // I
        cx += sp;
        glVertex2f(cx + s / 2, y); glVertex2f(cx + s / 2, y + s);
        // N
        cx += sp;
        glVertex2f(cx, y); glVertex2f(cx, y + s);
        glVertex2f(cx, y + s); glVertex2f(cx + s, y);
        glVertex2f(cx + s, y); glVertex2f(cx + s, y + s);
        // G
        cx += sp;
        glVertex2f(cx + s, y + s); glVertex2f(cx, y + s);
        glVertex2f(cx, y); glVertex2f(cx, y + s);
        glVertex2f(cx, y); glVertex2f(cx + s, y);
        glVertex2f(cx + s, y); glVertex2f(cx + s, y + s / 2);
        glVertex2f(cx + s / 2, y + s / 2); glVertex2f(cx + s, y + s / 2);
        // L
        cx += sp;
        glVertex2f(cx, y); glVertex2f(cx, y + s);
        glVertex2f(cx, y); glVertex2f(cx + s, y);
        // E
        cx += sp;
        glVertex2f(cx + s, y); glVertex2f(cx, y);
        glVertex2f(cx, y); glVertex2f(cx, y + s);
        glVertex2f(cx, y + s / 2); glVertex2f(cx + s * 0.8f, y + s / 2);
        glVertex2f(cx, y + s); glVertex2f(cx + s, y + s);
        // P
        cx += sp;
        glVertex2f(cx, y); glVertex2f(cx, y + s);
        glVertex2f(cx, y + s); glVertex2f(cx + s, y + s);
        glVertex2f(cx + s, y + s / 2); glVertex2f(cx + s, y + s);
        glVertex2f(cx, y + s / 2); glVertex2f(cx + s, y + s / 2);
        // L
        cx += sp;
        glVertex2f(cx, y); glVertex2f(cx, y + s);
        glVertex2f(cx, y); glVertex2f(cx + s, y);
        // A
        cx += sp;
        glVertex2f(cx, y); glVertex2f(cx, y + s);
        glVertex2f(cx, y + s); glVertex2f(cx + s, y + s);
        glVertex2f(cx + s, y); glVertex2f(cx + s, y + s);
        glVertex2f(cx, y + s / 2); glVertex2f(cx + s, y + s / 2);
        // Y
        cx += sp;
        glVertex2f(cx, y + s); glVertex2f(cx + s / 2, y + s / 2);
        glVertex2f(cx + s, y + s); glVertex2f(cx + s / 2, y + s / 2);
        glVertex2f(cx + s / 2, y + s / 2); glVertex2f(cx + s / 2, y);
        // E
        cx += sp;
        glVertex2f(cx + s, y); glVertex2f(cx, y);
        glVertex2f(cx, y); glVertex2f(cx, y + s);
        glVertex2f(cx, y + s / 2); glVertex2f(cx + s * 0.8f, y + s / 2);
        glVertex2f(cx, y + s); glVertex2f(cx + s, y + s);
        // R
        cx += sp;
        glVertex2f(cx, y); glVertex2f(cx, y + s);
        glVertex2f(cx, y + s); glVertex2f(cx + s, y + s);
        glVertex2f(cx + s, y + s / 2); glVertex2f(cx + s, y + s);
        glVertex2f(cx, y + s / 2); glVertex2f(cx + s, y + s / 2);
        glVertex2f(cx + s / 2, y + s / 2); glVertex2f(cx + s, y);

        glEnd();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
    }

    public static void main(String[] args) {
        new App().run();
    }
}