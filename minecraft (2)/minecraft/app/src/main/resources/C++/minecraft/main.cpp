#include <GLFW/glfw3.h>
#if defined(__APPLE__)
#include <OpenGL/gl.h>
#else
#include <GL/gl.h>
#endif

#include <iostream>
#include <vector>
#include <string>
#include <unordered_map>
#include <cmath>
#include <random>
#include <fstream>
#include <sstream>
#include <algorithm>

enum class GameState { MAIN_MENU, PLAYING };

class MinecraftApp {
private:
    GLFWwindow* window = nullptr;
    GameState currentState = GameState.MAIN_MENU;
    float panoramaAngle = 0.0f;

    static const int CHUNK_SIZE = 16;
    static const int RENDER_DISTANCE = 3;
    static const long long WORLD_SEED = 1337228LL;

    std::unordered_map<std::string, std::vector<std::vector<int>>> chunks;
    std::unordered_map<std::string, int> customHeights;
    std::unordered_map<std::string, int> blockMaterials;
    int selectedBlockType = 1;

    bool hasCraftingTable = false;
    float worldTime = 0.3f;

    float playerX = 8.0f;
    float playerY = 18.0f;
    float playerZ = 8.0f;
    float velocityY = 0.0f;
    bool isGrounded = false;

    float yaw = -90.0f;
    float pitch = -20.0f;
    double lastMouseX = 0;
    double lastMouseY = 0;
    bool isDragging = false;

    int windowWidth = 1920;
    int windowHeight = 1080;

    bool isMapOverview = false;
    bool isInventoryOpen = false;

    // Сенсорні змінні керування для телефону
    bool touchForward = false;
    bool touchBack = false;
    bool touchLeft = false;
    bool touchRight = false;

public:
    void run() {
        init();
        loadWorld();
        loop();
        saveWorld();
        glfwTerminate();
    }

private:
    void init() {
        if (!glfwInit()) {
            std::cerr << "Не вдалося ініціалізувати GLFW" << std::endl;
            exit(1);
        }

        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);

        GLFWmonitor* primaryMonitor = glfwGetPrimaryMonitor();
        const GLFWvidmode* vidMode = glfwGetVideoMode(primaryMonitor);
        if (vidMode) {
            windowWidth = vidMode->width;
            windowHeight = vidMode->height;
        }

        window = glfwCreateWindow(windowWidth, windowHeight, "Mobile 3D Minecraft C++ (Gradle)", primaryMonitor, NULL);
        if (!window) {
            std::cerr << "Не вдалося створити вікно гри" << std::endl;
            glfwTerminate();
            exit(1);
        }

        glfwSetWindowUserPointer(window, this);

        glfwSetFramebufferSizeCallback(window, [](GLFWwindow* win, int width, int height) {
            MinecraftApp* app = static_cast<MinecraftApp*>(glfwGetWindowUserPointer(win));
            app->windowWidth = width;
            app->windowHeight = height;
            glViewport(0, 0, width, height);
        });

        // Сенсорне управління через кліки/дотики
        glfwSetMouseButtonCallback(window, [](GLFWwindow* win, int button, int action, int mods) {
            MinecraftApp* app = static_cast<MinecraftApp*>(glfwGetWindowUserPointer(win));
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                double mx, my;
                glfwGetCursorPos(win, &mx, &my);
                float normX = (float)(mx / app->windowWidth) * 2.0f - 1.0f;
                float normY = 1.0f - (float)(my / app->windowHeight) * 2.0f;

                if (app->currentState == GameState::MAIN_MENU) {
                    if (action == GLFW_PRESS) {
                        app->currentState = GameState::PLAYING;
                    }
                } else if (app->currentState == GameState.PLAYING) {
                    if (action == GLFW_PRESS) {
                        // Віртуальні кнопки руху (джойстик зліва)
                        if (normX > -0.85f && normX < -0.70f && normY > -0.55f && normY < -0.40f) { app->touchForward = true; return; }
                        if (normX > -0.85f && normX < -0.70f && normY > -0.85f && normY < -0.70f) { app->touchBack = true; return; }
                        if (normX > -0.98f && normX < -0.83f && normY > -0.70f && normY < -0.55f) { app->touchLeft = true; return; }
                        if (normX > -0.72f && normX < -0.57f && normY > -0.70f && normY < -0.55f) { app->touchRight = true; return; }
                        if (normX > -0.85f && normX < -0.70f && normY > -0.40f && normY < -0.25f) {
                            if (app->isGrounded) { app->velocityY = 0.15f; app->isGrounded = false; }
                            return;
                        }

                        // Кнопки дій (справа)
                        if (normX > 0.60f && normX < 0.78f && normY > -0.75f && normY < -0.50f) {
                            app->breakBlock();
                            return;
                        }
                        if (normX > 0.80f && normX < 0.98f && normY > -0.75f && normY < -0.50f) {
                            app->placeBlock();
                            return;
                        }
                        if (normX > 0.80f && normX < 0.98f && normY > 0.75f && normY < 0.95f) {
                            app->isInventoryOpen = !app->isInventoryOpen;
                            return;
                        }

                        if (app->isInventoryOpen) {
                            if (normX > -0.2f && normX < 0.2f && normY > 0.1f && normY < 0.3f) {
                                app->hasCraftingTable = true;
                                std::cout << "[ІНФО] Верстак скрафчено!" << std::endl;
                            }
                        } else {
                            app->isDragging = true;
                            app->lastMouseX = mx;
                            app->lastMouseY = my;
                        }
                    } else if (action == GLFW_RELEASE) {
                        app->isDragging = false;
                        app->touchForward = false;
                        app->touchBack = false;
                        app->touchLeft = false;
                        app->touchRight = false;
                    }
                }
            }
        });

        // Обертання камери пальцем
        glfwSetCursorPosCallback(window, [](GLFWwindow* win, double xpos, double ypos) {
            MinecraftApp* app = static_cast<MinecraftApp*>(glfwGetWindowUserPointer(win));
            if (app->currentState == GameState.PLAYING && app->isDragging && !app->isMapOverview && !app->isInventoryOpen) {
                double xoffset = xpos - app->lastMouseX;
                double yoffset = app->lastMouseY - ypos;
                app->lastMouseX = xpos;
                app->lastMouseY = ypos;

                float sensitivity = 0.2f;
                app->yaw += (float)(xoffset * sensitivity);
                app->pitch += (float)(yoffset * sensitivity);

                if (app->pitch > 89.0f) app->pitch = 89.0f;
                if (app->pitch < -89.0f) app->pitch = -89.0f;
            }
        });

        glfwMakeContextCurrent(window);
        glfwSwapInterval(0);
        glfwShowWindow(window);

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
    }

    std::vector<std::vector<int>> generateChunkData(int chunkX, int chunkZ) {
        std::vector<std::vector<int>> heights(CHUNK_SIZE, std::vector<int>(CHUNK_SIZE));
        std::mt19937 random(WORLD_SEED + (long long)chunkX * 3418731287LL ^ (long long)chunkZ * 1328979875LL);
        
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int worldX = chunkX * CHUNK_SIZE + x;
                int worldZ = chunkZ * CHUNK_SIZE + z;
                
                float hillNoise1 = (float)(std::sin(worldX * 0.04f) * std::cos(worldZ * 0.04f) * 6.0f);
                float hillNoise2 = (float)(std::sin(worldX * 0.15f) * std::cos(worldZ * 0.15f) * 2.5f);
                int h = (int)(hillNoise1 + hillNoise2 + 14);
                heights[x][z] = h;
            }
        }
        return heights;
    }

    std::vector<std::vector<int>>& getChunk(int chunkX, int chunkZ) {
        std::string key = std::to_string(chunkX) + "," + std::to_string(chunkZ);
        if (chunks.find(key) == chunks.end()) {
            chunks[key] = generateChunkData(chunkX, chunkZ);
        }
        return chunks[key];
    }

    int getBlockHeight(int wx, int wz) {
        int cx = std::floor((float)wx / CHUNK_SIZE);
        int cz = std::floor((float)wz / CHUNK_SIZE);
        auto& chunk = getChunk(cx, cz);
        int lx = wx - cx * CHUNK_SIZE;
        int lz = wz - cz * CHUNK_SIZE;
        if (lx >= 0 && lx < CHUNK_SIZE && lz >= 0 && lz < CHUNK_SIZE) {
            std::string bKey = std::to_string(wx) + "," + std::to_string(wz);
            if (customHeights.find(bKey) != customHeights.end()) {
                return customHeights[bKey];
            }
            return chunk[lx][lz];
        }
        return 14;
    }

    void breakBlock() {
        int targetX = (int)std::floor(playerX + std::sin(toRadians(yaw)) * 1.5f);
        int targetZ = (int)std::floor(playerZ - std::cos(toRadians(yaw)) * 1.5f);
        int currentH = getBlockHeight(targetX, targetZ);
        if (currentH >= 0) {
            std::string bKey = std::to_string(targetX) + "," + std::to_string(targetZ);
            customHeights[bKey] = currentH - 1;
        }
    }

    void placeBlock() {
        int targetX = (int)std::floor(playerX + std::sin(toRadians(yaw)) * 1.5f);
        int targetZ = (int)std::floor(playerZ - std::cos(toRadians(yaw)) * 1.5f);
        int currentH = getBlockHeight(targetX, targetZ);
        int newH = currentH + 1;
        std::string bKey = std::to_string(targetX) + "," + std::to_string(targetZ);
        customHeights[bKey] = newH;
        blockMaterials[bKey] = selectedBlockType;
    }

    float toRadians(float degrees) {
        return degrees * 3.14159265358979323846f / 180.0f;
    }

    void loop() {
        while (!glfwWindowShouldClose(window)) {
            worldTime += 0.0001f;
            if (worldTime > 1.0f) worldTime = 0.0f;

            float sunAngle = (float)std::sin(worldTime * 2.0f * 3.14159265f);
            float skyBrightness = std::max(0.2f, (sunAngle + 1.0f) / 2.0f);
            glClearColor(0.52f * skyBrightness, 0.75f * skyBrightness, 1.0f * skyBrightness, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            if (currentState == GameState::MAIN_MENU) {
                renderMainMenu();
            } else {
                handleInput();
                renderGameWorld();
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    void handleInput() {
        if (isInventoryOpen) return;

        float speed = 0.12f;
        float rad = toRadians(yaw);
        if (touchForward) {
            playerX += (float)std::sin(rad) * speed;
            playerZ -= (float)std::cos(rad) * speed;
        }
        if (touchBack) {
            playerX -= (float)std::sin(rad) * speed;
            playerZ += (float)std::cos(rad) * speed;
        }
        if (touchLeft) {
            playerX -= (float)std::cos(rad) * speed;
            playerZ -= (float)std::sin(rad) * speed;
        }
        if (touchRight) {
            playerX += (float)std::cos(rad) * speed;
            playerZ += (float)std::sin(rad) * speed;
        }

        velocityY -= 0.008f;
        playerY += velocityY;

        int groundH = getBlockHeight((int)std::floor(playerX), (int)std::floor(playerZ));
        float groundHeight = groundH + 1.8f;
        if (playerY <= groundHeight) {
            playerY = groundHeight;
            velocityY = 0.0f;
            isGrounded = true;
        }
    }

    void renderMainMenu() {
        setupOrthographic();
        drawRect2D(-0.42f, -0.08f, 0.84f, 0.16f, 0.15f, 0.15f, 0.15f, 0.9f);
    }

    void renderGameWorld() {
        setupPerspective();
        glRotatef(pitch, 1.0f, 0.0f, 0.0f);
        glRotatef(yaw + 90.0f, 0.0f, 1.0f, 0.0f);
        glTranslatef(-playerX, -playerY, -playerZ);

        int pChunkX = (int)std::floor(playerX / CHUNK_SIZE);
        int pChunkZ = (int)std::floor(playerZ / CHUNK_SIZE);
        drawChunksAround(pChunkX, pChunkZ);

        renderHotbarUI();
    }

    void drawChunksAround(int centerCX, int centerCZ) {
        glColor3f(1.0f, 1.0f, 1.0f);
        for (int cx = centerCX - RENDER_DISTANCE; cx <= centerCX + RENDER_DISTANCE; cx++) {
            for (int cz = centerCZ - RENDER_DISTANCE; cz <= centerCZ + RENDER_DISTANCE; cz++) {
                auto& heights = getChunk(cx, cz);
                int startX = cx * CHUNK_SIZE;
                int startZ = cz * CHUNK_SIZE;

                for (int x = 0; x < CHUNK_SIZE; x++) {
                    for (int z = 0; z < CHUNK_SIZE; z++) {
                        int wx = startX + x;
                        int wz = startZ + z;
                        int h = getBlockHeight(wx, wz);

                        for (int y = 0; y <= h; y++) {
                            drawCube(wx, y, wz);
                        }
                    }
                }
            }
        }
    }

    void drawCube(float x, float y, float z) {
        glBegin(GL_QUADS);
        glVertex3f(x, y + 1, z + 1); glVertex3f(x + 1, y + 1, z + 1); glVertex3f(x + 1, y + 1, z); glVertex3f(x, y + 1, z);
        glVertex3f(x, y, z); glVertex3f(x + 1, y, z); glVertex3f(x + 1, y, z + 1); glVertex3f(x, y, z + 1);
        glVertex3f(x, y, z + 1); glVertex3f(x + 1, y, z + 1); glVertex3f(x + 1, y + 1, z + 1); glVertex3f(x, y + 1, z + 1);
        glVertex3f(x + 1, y, z); glVertex3f(x, y, z); glVertex3f(x, y + 1, z); glVertex3f(x + 1, y + 1, z);
        glVertex3f(x, y, z); glVertex3f(x, y, z + 1); glVertex3f(x + 1, y + 1, z + 1); glVertex3f(x + 1, y + 1, z);
        glVertex3f(x + 1, y, z + 1); glVertex3f(x + 1, y, z); glVertex3f(x + 1, y + 1, z); glVertex3f(x + 1, y + 1, z + 1);
        glEnd();
    }

    void renderHotbarUI() {
        setupOrthographic();
        glDisable(GL_DEPTH_TEST);
        drawRect2D(-0.6f, -0.9f, 0.9f, 0.15f, 0.2f, 0.2f, 0.2f, 0.6f);
        glEnable(GL_DEPTH_TEST);
    }

    void setupPerspective() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float aspect = (float)windowWidth / (float)windowHeight;
        float fov = 70.0f;
        float zNear = 0.1f;
        float zFar = 100.0f;
        float ymax = zNear * std::tan(toRadians(fov / 2.0f));
        float xmax = ymax * aspect;
        glFrustum(-xmax, xmax, -ymax, ymax, zNear, zFar);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    void setupOrthographic() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(-1.0, 1.0, -1.0, 1.0, -1.0, 1.0);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glDisable(GL_CULL_FACE);
    }

    void drawRect2D(float x, float y, float w, float h, float r, float g, float b, float a) {
        glDisable(GL_DEPTH_TEST);
        glColor4f(r, g, b, a);
        glBegin(GL_QUADS);
        glVertex2f(x, y); glVertex2f(x + w, y); glVertex2f(x + w, y + h); glVertex2f(x, y + h);
        glEnd();
        glEnable(GL_DEPTH_TEST);
    }

    void saveWorld() {
        std::ofstream writer("world.dat");
        if (writer.is_open()) {
            writer << customHeights.size() << "\n";
            for (auto const& [key, val] + : customHeights) {
                writer << key << ":" << val << "\n";
            }
            writer << playerX << "," << playerY << "," << playerZ << "\n";
            writer.close();
        }
    }

    void loadWorld() {
        std::ifstream reader("world.dat");
        if (reader.is_open()) {
            int hSize;
            reader >> hSize;
            for (int i = 0; i < hSize; i++) {
                std::string line;
                reader >> line;
                size_t delimiterPos = line.find(":");
                if (delimiterPos != std::string::npos) {
                    std::string key = line.substr(0, delimiterPos);
                    int val = std::stoi(line.substr(delimiterPos + 1));
                    customHeights[key] = val;
                }
            }
            char comma;
            reader >> playerX >> comma >> playerY >> comma >> playerZ;
            reader.close();
        }
    }
};

int main() {
    MinecraftApp app;
    app.run();
    return 0;
}