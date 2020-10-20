package com.incendiary;

import javafx.scene.input.KeyCode;

import java.util.Arrays;
import java.util.Random;

public class Chip8Emulator {

    //http://devernay.free.fr/hacks/chip8/C8TECH10.HTM#00E0

    //0-F sprites
    private static final byte[] HEX_SPRITES = new byte[] {
            (byte)0xF0, (byte)0x90, (byte)0x90, (byte)0x90, (byte)0xF0,  // 0
            (byte)0x20, (byte)0x60, (byte)0x20, (byte)0x20, (byte)0x70,  // 1
            (byte)0xF0, (byte)0x10, (byte)0xF0, (byte)0x80, (byte)0xF0,  // 2
            (byte)0xF0, (byte)0x10, (byte)0xF0, (byte)0x10, (byte)0xF0,  // 3
            (byte)0x90, (byte)0x90, (byte)0xF0, (byte)0x10, (byte)0x10,  // 4
            (byte)0xF0, (byte)0x80, (byte)0xF0, (byte)0x10, (byte)0xF0,  // 5
            (byte)0xF0, (byte)0x80, (byte)0xF0, (byte)0x90, (byte)0xF0,  // 6
            (byte)0xF0, (byte)0x10, (byte)0x20, (byte)0x40, (byte)0x40,  // 7
            (byte)0xF0, (byte)0x90, (byte)0xF0, (byte)0x90, (byte)0xF0,  // 8
            (byte)0xF0, (byte)0x90, (byte)0xF0, (byte)0x10, (byte)0xF0,  // 9
            (byte)0xF0, (byte)0x90, (byte)0xF0, (byte)0x90, (byte)0x90,  // A
            (byte)0xE0, (byte)0x90, (byte)0xE0, (byte)0x90, (byte)0xE0,  // B
            (byte)0xF0, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0xF0,  // C
            (byte)0xE0, (byte)0x90, (byte)0x90, (byte)0x90, (byte)0xE0,  // D
            (byte)0xF0, (byte)0x80, (byte)0xF0, (byte)0x80, (byte)0xF0,  // E
            (byte)0xF0, (byte)0x80, (byte)0xF0, (byte)0x80, (byte)0x80   // F
    };

    public static final int VIRTUAL_WIDTH = 64;
    public static final int VIRTUAL_HEIGHT = 32;

    //this is signed, but will be used as if it is unsigned
    private byte[] memory = new byte[4096];

    private byte[] register = new byte[16];

    //every bit is a pixel. 'drawing' is handled by XOR'ing the memory with bytes from memory
    private boolean[][] screen = new boolean[VIRTUAL_WIDTH][VIRTUAL_HEIGHT];

    private Thread thread;
    private Thread soundTimerThread;
    private Thread delayTimerThread;
    private boolean running = true;

    private int pc = 512;

    private int sp = 0;

    private int[] stack = new int[16];

    private int registerI = 0;

    //decrement at 60Hz (1000/60 ms wait)
    private int dt = 0;
    private int st = 0;

    private boolean[] keys = new boolean[16];

    public Chip8Emulator(byte[] program) {
        System.arraycopy(HEX_SPRITES, 0, memory, 0, HEX_SPRITES.length);
        System.arraycopy(program, 0, memory, 512, program.length);
    }

    public void start() {
        thread = new Thread(() -> {
            while(running) {

                //fetch
                int instruction = (Byte.toUnsignedInt(memory[pc]) << 8) | Byte.toUnsignedInt(memory[pc + 1]);

                //execute
                execute(instruction);

                //increment pc
                pc += 2;

                //wait
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        soundTimerThread = new Thread(() -> {
            while(running) {
                try {
                    Thread.sleep(1000/60);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if(st > 0) {
                    st--;
                }
            }
        });
        delayTimerThread = new Thread(() -> {
            while(running) {
                try {
                    Thread.sleep(1000/60);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if(dt > 0) {
                    dt--;
                }
            }
        });
        thread.start();
        soundTimerThread.start();
        delayTimerThread.start();
    }

    public void stop() throws InterruptedException {
        running = false;
        thread.join();
        soundTimerThread.join();
        delayTimerThread.join();
    }

    public boolean[][] getScreenState() {
        return screen;
    }

    private void execute(int instruction) {
        log("Executing " + toHexString(instruction));

        int[] nibbles = new int[] {
                instruction >>> 12 & 0xF,
                instruction >>> 8 & 0xF,
                instruction >>> 4 & 0xF,
                instruction & 0xF,
        };

        int addr = (nibbles[1] << 8) | (nibbles[2] << 4) | nibbles[3];

        switch (nibbles[0]) {
            case 0:
                if (nibbles[1] == 0 && nibbles[2] == 0xE && nibbles[3] == 0) { //CLS
                    eraseScreen();
                } else if (nibbles[1] == 0 && nibbles[2] == 0xE && nibbles[3] == 0xE) { //RET
                    sp--;
                    pc = stack[sp];
                    log("Returning to memory location " + toHexString(pc, 6) + " from stack");
                } else {
                    throw new IllegalStateException("UNKNOWN INSTRUCTION: " + toHexString(instruction));
                }
                break;
            case 1: // JP addr
                pc = addr - 2;
                break;
            case 2: //CALL addr
                log("Calling to memory location " + toHexString(addr, 6) + " and pushing " + toHexString(pc, 6) + " to stack");
                stack[sp] = pc;
                sp++;
                pc = addr - 2;
                break;
            case 3: //SE Vx, byte
                if (register[nibbles[1]] == ((byte) ((nibbles[2] << 4) | nibbles[3]))) {
                    pc += 2;
                }
                break;
            case 4: //SNE Vx, byte
                if (register[nibbles[1]] != ((byte) ((nibbles[2] << 4) | nibbles[3]))) {
                    pc += 2;
                }
                break;
            case 5: //SE Vx, Vy
                if(register[nibbles[1]] == register[nibbles[2]]) {
                    pc += 2;
                }
                break;
            case 6: //LD Vx, byte
                register[nibbles[1]] = (byte) ((nibbles[2] << 4) | nibbles[3]);
                log("Stored " + toHexString(register[nibbles[1]]) + " in register " + toHexString(nibbles[1]));
                break;
            case 7: // ADD Vx, byte
                register[nibbles[1]] = (byte) (register[nibbles[1]] + ((byte) ((nibbles[2] << 4) | nibbles[3])));
                break;
            case 8:
                if (nibbles[3] == 0) { //LD Vx, Vy
                    register[nibbles[1]] = register[nibbles[2]];
                } else if (nibbles[3] == 1) { // OR Vx, Vy
                    register[nibbles[1]] = (byte) (Byte.toUnsignedInt(register[nibbles[1]]) | Byte.toUnsignedInt(register[nibbles[2]]));
                } else if (nibbles[3] == 2) { // AND Vx, Vy
                    register[nibbles[1]] = (byte) (Byte.toUnsignedInt(register[nibbles[1]]) & Byte.toUnsignedInt(register[nibbles[2]]));
                } else if(nibbles[3] == 3) { //XOR Vx, Vy
                    register[nibbles[1]] = (byte) (Byte.toUnsignedInt(register[nibbles[1]]) ^ Byte.toUnsignedInt(register[nibbles[2]]));
                } else if (nibbles[3] == 4) { //ADD Vx, Vy
                    int sum = Byte.toUnsignedInt(register[nibbles[1]]) + Byte.toUnsignedInt(register[nibbles[2]]);
                    register[0xF] = (byte) (sum > 255 ? 1 : 0);
                    register[nibbles[1]] = (byte) (sum & 0xFF);
                } else if (nibbles[3] == 5) { //SUB Vx, Vy
                    int difference = Byte.toUnsignedInt(register[nibbles[1]]) - Byte.toUnsignedInt(register[nibbles[2]]);
                    register[0xF] = (byte) (Byte.toUnsignedInt(register[nibbles[1]]) > Byte.toUnsignedInt(register[nibbles[2]]) ? 1 : 0);
                    register[nibbles[1]] = (byte) (difference & 0xFF);
                } else if(nibbles[3] == 6) { //SHR Vx
                    register[0xF] = (byte) (Byte.toUnsignedInt(register[nibbles[1]]) & 1);
                    register[nibbles[1]] = (byte) (Byte.toUnsignedInt(register[nibbles[1]]) >> 1);
                } else if(nibbles[3] == 0xE) { //SHL Vx
                    register[0xF] = (byte) (Byte.toUnsignedInt(register[nibbles[1]]) & 0x80);
                    register[nibbles[1]] = (byte) (Byte.toUnsignedInt(register[nibbles[1]]) << 1);
                } else {
                    throw new IllegalStateException("UNKNOWN INSTRUCTION: " + toHexString(instruction));
                }
            case 9: //SNE Vx, Vy
                if(register[nibbles[1]] != register[nibbles[2]]) {
                    pc += 2;
                }
                break;
            case 0xA: //LD I, addr
                this.registerI = addr;
                log("Stored " + toHexString(addr, 6) + " in register I");
                break;
            case 0xC: //RND Vx, byte
                register[nibbles[1]] = (byte) ((nibbles[2] << 4) | nibbles[3] & new Random().nextInt() % 256);
                break;
            case 0xD: //DRW Vx, Vy, nibble
                int x = Byte.toUnsignedInt(register[nibbles[1]]);
                int y = Byte.toUnsignedInt(register[nibbles[2]]);
                int numBytes = nibbles[3];

                eraseScreen();
                writeSpriteToScreen(x, y, numBytes);
                break;
            case 0xE:
                if(nibbles[2] == 9 && nibbles[3] == 0xE) { //SKP Vx
                    if(keys[register[nibbles[1]]]) {
                        pc += 2;
                    }
                } else if(nibbles[2] == 0xA && nibbles[3] == 1) { //SKNP Vx
                    if(!keys[register[nibbles[1]]]) {
                        pc += 2;
                    }
                } else {
                    throw new IllegalStateException("UNKNOWN INSTRUCTION: " + toHexString(instruction));
                }
                break;
            case 0xF:
                if(nibbles[2] == 0 && nibbles[3] == 7) { //LD Vx, DT
                    register[nibbles[1]] = (byte)dt;
                } else if(nibbles[2] == 1 && nibbles[3] == 5) { //LD DT, Vx
                    dt = register[nibbles[1]];
                } else if(nibbles[2] == 1 && nibbles[3] == 8) { //LD DT, Vx
                    st = register[nibbles[1]];
                } else if(nibbles[2] == 1 && nibbles[3] == 0xE) { //ADD I, Vx
                    registerI += Byte.toUnsignedInt(register[nibbles[1]]);
                } else if(nibbles[2] == 3 && nibbles[3] == 3) { //LD B, Vx
                    int num = Byte.toUnsignedInt(register[nibbles[1]]);

                    int hundreds = num / 100;
                    int tens = (num % 100) / 10;
                    int ones = (num % 100) % 10;

                    memory[registerI] = (byte) hundreds;
                    memory[registerI + 1] = (byte) tens;
                    memory[registerI + 2] = (byte) ones;

                    log("Storing decimal representation of " + String.format("%03d", num) + " in memory locations " + toHexString(registerI, 6) + " through " + toHexString(registerI + 2, 6));
                } else if(nibbles[2] == 5 && nibbles[3] == 5) { //LD [I], Vx
                    for(int i = 0; i < nibbles[1]; i ++) {
                        memory[registerI + i] = register[i];
                    }
                } else if(nibbles[2] == 6 && nibbles[3] == 5) { //LD Vx, [I]
                    for (int i = 0; i < nibbles[1]; i++) {
                        register[i] = memory[registerI + i];
                    }
                } else if(nibbles[2] == 2 && nibbles[3] == 9) { //LD F, Vx
                    registerI = nibbles[1] * 5;
                } else {
                    throw new IllegalStateException("UNKNOWN INSTRUCTION: " + toHexString(instruction));
                }
                break;
            default:
                throw new IllegalStateException("UNKNOWN INSTRUCTION: " + toHexString(instruction));
        }
    }

    public void keyInput(KeyCode keyCode, boolean isDown) {
        switch (keyCode) {
            case NUMPAD0:
                keys[0] = isDown;
                break;
            case NUMPAD1:
                keys[1] = isDown;
                break;
            case NUMPAD2:
                keys[2] = isDown;
                break;
            case NUMPAD3:
                keys[3] = isDown;
                break;
            case NUMPAD4:
                keys[4] = isDown;
                break;
            case NUMPAD5:
                keys[5] = isDown;
                break;
            case NUMPAD6:
                keys[6] = isDown;
                break;
            case NUMPAD7:
                keys[7] = isDown;
                break;
            case NUMPAD8:
                keys[8] = isDown;
                break;
            case NUMPAD9:
                keys[9] = isDown;
                break;
            case Q:
                keys[0xA] = isDown;
                break;
            case W:
                keys[0xB] = isDown;
                break;
            case E:
                keys[0xC] = isDown;
                break;
            case R:
                keys[0xD] = isDown;
                break;
            case T:
                keys[0xE] = isDown;
                break;
            case Y:
                keys[0xF] = isDown;
                break;
        }
    }

    private String toHexString(int num) {
        return String.format("%04x", num).toUpperCase();
    }

    private String toHexString(int num, int digits) {
        return String.format("%0" + digits + "x", num).toUpperCase();
    }

    private void log(String text) {
        //System.out.println(text);
    }

    private void writeSpriteToScreen(int x, int y, int numBytes) {
        for(int i = 0; i < numBytes; i ++) {
            int sprite = Byte.toUnsignedInt(memory[registerI + i]);

            for(int j = 0; j < 8; j ++) {

                if((sprite & 0x80) > 0) {
                    if(flipPixel(x + j, y + i)) {
                        register[0xF] = 1;
                    }
                }

                sprite <<= 1;
            }
        }
    }

    private void eraseScreen() {
        for(int y = 0; y < VIRTUAL_HEIGHT; y ++) {
            for(int x = 0; x < VIRTUAL_WIDTH; x ++) {
                screen[x][y] = false;
            }
        }
    }

    private boolean flipPixel(int x, int y) {
        if(x < screen.length && y < screen[x].length) {
            screen[x][y] = !screen[x][y];
            log("Writing pixel " + (screen[x][y] ? 1 : 0) + " to screen[" + toHexString(x, 2) + "][" + toHexString(y) + "]");
            return !screen[x][y];
        }
        return false;
    }

}
