//Nguyen Thanh Dat - 202419040
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

// 1. ENUM TRẠNG THÁI
enum DeviceStatus {
    AVAILABLE,
    BORROWED,
    UNDER_MAINTENANCE
}

// 2. EXCEPTION
class DuplicateIdException extends RuntimeException {
    private final String duplicateId;

    public DuplicateIdException(String duplicateId) {
        super("Mã '" + duplicateId + "' đã tồn tại trong kho.");
        this.duplicateId = duplicateId;
    }

    public String getDuplicateId() {
        return duplicateId;
    }
}

class NoSuitableItemException extends RuntimeException {
    public NoSuitableItemException() {
        super("Không có thiết bị phù hợp.");
    }

    public NoSuitableItemException(String message) {
        super(message);
    }
}

class DeviceNotReadyException extends RuntimeException {
    private final String deviceId;
    private final DeviceStatus currentStatus;

    public DeviceNotReadyException(String deviceId, DeviceStatus currentStatus) {
        super("Thiết bị '" + deviceId + "' chưa sẵn sàng để cấp phát (trạng thái: " + currentStatus + ").");
        this.deviceId = deviceId;
        this.currentStatus = currentStatus;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public DeviceStatus getCurrentStatus() {
        return currentStatus;
    }
}

// 3. INTERFACE NHẬN DIỆN + LỚP TRỪU TƯỢNG TeachingDevice
interface IIdentifiable {
    String getId();
}

abstract class TeachingDevice implements IIdentifiable {
    private final String id;
    private final String name;
    private final String storageLocation;
    private DeviceStatus status;

    protected TeachingDevice(String id, String name, String storageLocation) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.storageLocation = requireText(storageLocation, "storageLocation");
        this.status = DeviceStatus.AVAILABLE; // trạng thái ban đầu
    }

    @Override
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getStorageLocation() {
        return storageLocation;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    // Chỉ lớp con mới được đổi trạng thái trực tiếp 
    protected void setStatus(DeviceStatus status) {
        this.status = status;
    }

    // ---- Hành vi thay đổi theo loại thiết bị -> đa hình ----
    public abstract boolean isReadyForUse();

    public abstract double getDefaultPriorityScore();

    // ---- Hành vi chung, nằm ở lớp cơ sở ----
    public void borrow() {
        // Gọi isReadyForUse() -> thực tế chạy phiên bản của lớp con (đa hình)
        if (!isReadyForUse()) {
            throw new DeviceNotReadyException(id, status);
        }
        status = DeviceStatus.BORROWED;
    }

    public void returnDevice() {
        if (status != DeviceStatus.BORROWED) {
            throw new IllegalStateException("Thiết bị '" + id + "' không ở trạng thái BORROWED (hiện tại: "
                    + status + ") nên không thể trả.");
        }
        status = DeviceStatus.AVAILABLE;
    }

    // Hai hàm phụ để có thể kiểm thử trạng thái UNDER_MAINTENANCE
    public void sendToMaintenance() {
        if (status == DeviceStatus.BORROWED) {
            throw new IllegalStateException("Thiết bị '" + id + "' đang được mượn, không thể đưa đi bảo trì.");
        }
        status = DeviceStatus.UNDER_MAINTENANCE;
    }

    public void finishMaintenance() {
        if (status == DeviceStatus.UNDER_MAINTENANCE) {
            status = DeviceStatus.AVAILABLE;
        }
    }

    @Override
    public String toString() {
        return "[" + id + "] " + name + " @ " + storageLocation + " - " + status;
    }

    // ---- Hàm kiểm tra dữ liệu dùng chung cho các lớp con ----
    protected static String requireText(String value, String paramName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(paramName + " không được null hoặc rỗng.");
        }
        return value.trim();
    }

    protected static int requirePercent(int value, String paramName) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(paramName + " phải nằm trong khoảng 0..100 (nhận được " + value + ").");
        }
        return value;
    }
}

// 4. CÁC LỚP DẪN XUẤT
class ProjectorRemote extends TeachingDevice {
    public static final int MIN_BATTERY_TO_USE = 20;

    private final int batteryLevel;

    public ProjectorRemote(String id, String name, String storageLocation, int batteryLevel) {
        super(id, name, storageLocation);
        this.batteryLevel = requirePercent(batteryLevel, "batteryLevel");
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    @Override
    public boolean isReadyForUse() {
        return getStatus() == DeviceStatus.AVAILABLE && batteryLevel >= MIN_BATTERY_TO_USE;
    }

    @Override
    public double getDefaultPriorityScore() {
        return batteryLevel;
    }

    @Override
    public String toString() {
        return super.toString() + " | Pin: " + batteryLevel + "%";
    }
}

class AirConditionerRemote extends TeachingDevice {
    private final Set<String> compatibleRooms;

    public AirConditionerRemote(String id, String name, String storageLocation, Iterable<String> compatibleRooms) {
        super(id, name, storageLocation);
        Objects.requireNonNull(compatibleRooms, "compatibleRooms không được null");

        // TreeSet với CASE_INSENSITIVE_ORDER: "a203" và "A203" được coi là một phòng
        Set<String> rooms = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String room : compatibleRooms) {
            if (room != null && !room.trim().isEmpty()) {
                rooms.add(room.trim());
            }
        }
        // Bọc unmodifiable -> bên ngoài chỉ đọc được, không sửa được
        this.compatibleRooms = Collections.unmodifiableSet(rooms);
    }

    public Set<String> getCompatibleRooms() {
        return compatibleRooms;
    }

    public boolean isCompatibleWith(String roomId) {
        return roomId != null && !roomId.trim().isEmpty() && compatibleRooms.contains(roomId.trim());
    }

    @Override
    public boolean isReadyForUse() {
        return getStatus() == DeviceStatus.AVAILABLE;
    }

    @Override
    public double getDefaultPriorityScore() {
        return compatibleRooms.isEmpty() ? 0 : 100;
    }

    public double getPriorityScoreForRoom(String roomId) {
        return isCompatibleWith(roomId) ? 100 : 0;
    }

    @Override
    public String toString() {
        return super.toString() + " | Phòng: " + String.join(", ", compatibleRooms);
    }
}

class WirelessMicrophone extends TeachingDevice {
    public static final int MIN_BATTERY_TO_USE = 30;
    public static final int MIN_SIGNAL_TO_USE = 40;

    private final int batteryLevel;
    private final int signalQuality;

    public WirelessMicrophone(String id, String name, String storageLocation, int batteryLevel, int signalQuality) {
        super(id, name, storageLocation);
        this.batteryLevel = requirePercent(batteryLevel, "batteryLevel");
        this.signalQuality = requirePercent(signalQuality, "signalQuality");
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public int getSignalQuality() {
        return signalQuality;
    }

    @Override
    public boolean isReadyForUse() {
        return getStatus() == DeviceStatus.AVAILABLE
                && batteryLevel >= MIN_BATTERY_TO_USE
                && signalQuality >= MIN_SIGNAL_TO_USE;
    }

    @Override
    public double getDefaultPriorityScore() {
        return (batteryLevel + signalQuality) / 2.0;
    }

    @Override
    public String toString() {
        return super.toString() + " | Pin: " + batteryLevel + "% | Tín hiệu: " + signalQuality + "%";
    }
}

// 5. KHO LƯU TRỮ TỔNG QUÁT
interface IRepository<T extends IIdentifiable> {
    void add(T item);

    boolean remove(String id);

    T findById(String id); // trả về null nếu không tìm thấy

    List<T> getAll();

    List<T> findAll(Predicate<T> condition);
}

class InMemoryRepository<T extends IIdentifiable> implements IRepository<T> {
    private final List<T> items = new ArrayList<>();

    @Override
    public void add(T item) {
        Objects.requireNonNull(item, "Không chấp nhận phần tử null.");

        if (findById(item.getId()) != null) {
            throw new DuplicateIdException(item.getId());
        }
        items.add(item);
    }

    @Override
    public boolean remove(String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(id)) {
                items.remove(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public T findById(String id) {
        for (T item : items) {
            if (item.getId().equals(id)) {
                return item;
            }
        }
        return null;
    }

    @Override
    public List<T> getAll() {
        return List.copyOf(items);
    }

    @Override
    public List<T> findAll(Predicate<T> condition) {
        Objects.requireNonNull(condition, "condition không được null");

        List<T> result = new ArrayList<>();
        for (T item : items) {
            if (condition.test(item)) {
                result.add(item);
            }
        }
        return Collections.unmodifiableList(result);
    }
}

// 6. CHIẾN LƯỢC LỰA CHỌN (STRATEGY + GENERIC)
interface ISelectionStrategy<T> {
    T select(List<T> candidates);
}

class HighestPriorityStrategy<T> implements ISelectionStrategy<T> {
    private final ToDoubleFunction<T> scoreSelector;

    public HighestPriorityStrategy(ToDoubleFunction<T> scoreSelector) {
        this.scoreSelector = Objects.requireNonNull(scoreSelector, "scoreSelector không được null");
    }

    @Override
    public T select(List<T> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new NoSuitableItemException("Danh sách ứng viên rỗng.");
        }

        T best = candidates.get(0);
        double bestScore = scoreSelector.applyAsDouble(best);

        for (int i = 1; i < candidates.size(); i++) {
            double score = scoreSelector.applyAsDouble(candidates.get(i));
            if (score > bestScore) {
                best = candidates.get(i);
                bestScore = score;
            }
        }
        return best;
    }
}

class FirstAvailableStrategy<T> implements ISelectionStrategy<T> {
    @Override
    public T select(List<T> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new NoSuitableItemException("Danh sách ứng viên rỗng.");
        }
        return candidates.get(0);
    }
}

// 7. DỊCH VỤ CẤP PHÁT TỔNG QUÁT
class AllocationService<T extends TeachingDevice> {
    private final IRepository<T> repository;
    private ISelectionStrategy<T> strategy;

    public AllocationService(IRepository<T> repository, ISelectionStrategy<T> strategy) {
        this.repository = Objects.requireNonNull(repository, "repository không được null");
        this.strategy = Objects.requireNonNull(strategy, "strategy không được null");
    }

    public void changeStrategy(ISelectionStrategy<T> newStrategy) {
        this.strategy = Objects.requireNonNull(newStrategy, "newStrategy không được null");
    }

    // additionalCondition có thể là null (bỏ qua điều kiện bổ sung)
    public T allocate(Predicate<T> additionalCondition) {
        List<T> candidates = repository.findAll(device ->
                device.isReadyForUse()
                        && (additionalCondition == null || additionalCondition.test(device)));

        // 4: chọn bằng chiến lược hiện tại (ném NoSuitableItemException nếu rỗng)
        T selected = strategy.select(candidates);

        // 5 + 6: mượn và trả về
        selected.borrow();
        return selected;
    }

    public T allocate() {
        return allocate(null);
    }

    // Trả thiết bị về kho
    public void release(String deviceId) {
        T device = repository.findById(deviceId);
        if (device == null) {
            throw new NoSuchElementException("Không tìm thấy thiết bị '" + deviceId + "' trong kho.");
        }
        device.returnDevice();
    }
}

// 8. CÁC LỚP PHỤ CHỈ DÙNG ĐỂ DEMO TÍNH MỞ RỘNG
class InteractiveDisplayRemote extends TeachingDevice {
    private final boolean screenResponsive;

    public InteractiveDisplayRemote(String id, String name, String storageLocation, boolean screenResponsive) {
        super(id, name, storageLocation);
        this.screenResponsive = screenResponsive;
    }

    public boolean isScreenResponsive() {
        return screenResponsive;
    }

    @Override
    public boolean isReadyForUse() {
        return getStatus() == DeviceStatus.AVAILABLE && screenResponsive;
    }

    @Override
    public double getDefaultPriorityScore() {
        return screenResponsive ? 100 : 0;
    }
}

record RoomInfo(String id, String building) implements IIdentifiable {
    @Override
    public String getId() {
        return id;
    }
}

// 9. CHƯƠNG TRÌNH CHÍNH - KỊCH BẢN KIỂM THỬ
public class Main {

    public static void main(String[] args) {
        // Để in tiếng Việt có dấu đúng
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        // 3 điều khiển máy chiếu
        ProjectorRemote prj1 = new ProjectorRemote("PRJ-01", "Remote máy chiếu Epson", "Kho A", 85);
        ProjectorRemote prj2 = new ProjectorRemote("PRJ-02", "Remote máy chiếu Sony", "Kho A", 50);
        ProjectorRemote prj3 = new ProjectorRemote("PRJ-03", "Remote máy chiếu BenQ", "Kho B", 15); // pin yếu -> không sẵn sàng

        // 2 điều khiển điều hòa
        AirConditionerRemote ac1 = new AirConditionerRemote("AC-01", "Remote điều hòa Daikin", "Kho A",
                List.of("A101", "A203"));
        AirConditionerRemote ac2 = new AirConditionerRemote("AC-02", "Remote điều hòa Panasonic", "Kho B",
                List.of("B201", "B202"));

        // 3 micro
        WirelessMicrophone mic1 = new WirelessMicrophone("MIC-01", "Micro Shure", "Kho C", 90, 30);      // tín hiệu yếu -> không sẵn sàng
        WirelessMicrophone mic2 = new WirelessMicrophone("MIC-02", "Micro Sennheiser", "Kho C", 60, 80); // điểm 70
        WirelessMicrophone mic3 = new WirelessMicrophone("MIC-03", "Micro Boya", "Kho C", 95, 90);       // điểm 92.5

        // Mỗi loại thiết bị một repository, nhưng cùng MỘT lớp generic InMemoryRepository<T>
        InMemoryRepository<ProjectorRemote> projectorRepo = new InMemoryRepository<>();
        InMemoryRepository<AirConditionerRemote> acRepo = new InMemoryRepository<>();
        InMemoryRepository<WirelessMicrophone> micRepo = new InMemoryRepository<>();

        // ---------- TEST 1: Thêm thiết bị vào repository ----------
        printHeader("TEST 1: Thêm thiết bị vào repository");
        projectorRepo.add(prj1);
        projectorRepo.add(prj2);
        projectorRepo.add(prj3);
        acRepo.add(ac1);
        acRepo.add(ac2);
        micRepo.add(mic1);
        micRepo.add(mic2);
        micRepo.add(mic3);
        System.out.println("Máy chiếu: " + projectorRepo.getAll().size()
                + " | Điều hòa: " + acRepo.getAll().size()
                + " | Micro: " + micRepo.getAll().size());
        printList("Danh sách micro", micRepo.getAll());

        // ---------- TEST 2: Thêm hai thiết bị trùng mã ----------
        printHeader("TEST 2: Thêm thiết bị trùng mã");
        expect(DuplicateIdException.class,
                () -> projectorRepo.add(new ProjectorRemote("PRJ-01", "Remote trùng mã", "Kho Z", 70)),
                "Thêm PRJ-01 lần 2");
        expect(NullPointerException.class,
                () -> projectorRepo.add(null),
                "Thêm phần tử null");

        // ---------- TEST 3: Tìm thiết bị theo mã ----------
        printHeader("TEST 3: Tìm thiết bị theo mã");
        System.out.println("findById(\"PRJ-02\") -> " + projectorRepo.findById("PRJ-02"));
        System.out.println("findById(\"PRJ-99\") -> " + projectorRepo.findById("PRJ-99"));
        System.out.println("remove(\"PRJ-99\")   -> " + projectorRepo.remove("PRJ-99") + " (không tìm thấy nên false)");

        // ---------- TEST 4: Lọc các thiết bị sẵn sàng ----------
        printHeader("TEST 4: Lọc các thiết bị sẵn sàng");
        printList("Máy chiếu sẵn sàng (pin >= 20%)", projectorRepo.findAll(d -> d.isReadyForUse()));
        printList("Điều hòa sẵn sàng", acRepo.findAll(d -> d.isReadyForUse()));
        printList("Micro sẵn sàng (pin >= 30%, tín hiệu >= 40%)", micRepo.findAll(d -> d.isReadyForUse()));

        // ---------- TEST 5: Máy chiếu có điểm cao nhất ----------
        printHeader("TEST 5: Chọn điều khiển máy chiếu có điểm cao nhất");
        AllocationService<ProjectorRemote> projectorService = new AllocationService<>(
                projectorRepo,
                new HighestPriorityStrategy<>(d -> d.getDefaultPriorityScore()));

        ProjectorRemote selectedProjector = projectorService.allocate(d -> true);
        System.out.println("Được chọn: " + selectedProjector + "  (kỳ vọng PRJ-01)");

        // ---------- TEST 6: Điều hòa tương thích với phòng ----------
        printHeader("TEST 6: Chọn điều khiển điều hòa tương thích phòng A203");
        AllocationService<AirConditionerRemote> acService = new AllocationService<>(
                acRepo,
                new HighestPriorityStrategy<>(d -> d.getDefaultPriorityScore()));

        AirConditionerRemote selectedAc = acService.allocate(d -> d.isCompatibleWith("A203"));
        System.out.println("Được chọn: " + selectedAc + "  (kỳ vọng AC-01)");

        // ---------- TEST 7: Micro đầu tiên sẵn sàng ----------
        printHeader("TEST 7: Chọn micro đầu tiên sẵn sàng (FirstAvailableStrategy)");
        AllocationService<WirelessMicrophone> micService = new AllocationService<>(
                micRepo,
                new FirstAvailableStrategy<>());

        WirelessMicrophone firstMic = micService.allocate(d -> true);
        System.out.println("Được chọn: " + firstMic + "  (kỳ vọng MIC-02, vì MIC-01 tín hiệu yếu)");

        // ---------- TEST 8: Đổi chiến lược trong thời gian chạy ----------
        printHeader("TEST 8: Đổi chiến lược trong thời gian chạy");
        micService.release(firstMic.getId()); // trả MIC-02 về kho để so sánh công bằng
        System.out.println("Đã trả " + firstMic.getId() + ", trạng thái: " + firstMic.getStatus());

        printList("Micro sẵn sàng lúc này", micRepo.findAll(d -> d.isReadyForUse()));

        micService.changeStrategy(new HighestPriorityStrategy<>(d -> d.getDefaultPriorityScore()));
        WirelessMicrophone bestMic = micService.allocate(d -> true);
        System.out.println("Sau khi đổi sang HighestPriorityStrategy, được chọn: " + bestMic
                + "  (kỳ vọng MIC-03, điểm 92.5)");
        System.out.println("=> Trước đó FirstAvailable chọn MIC-02, giờ HighestPriority chọn MIC-03: cùng service, khác hành vi.");

        // ---------- TEST 9: Không có ứng viên phù hợp ----------
        printHeader("TEST 9: Cấp phát khi không có ứng viên phù hợp");
        expect(NoSuitableItemException.class,
                () -> acService.allocate(d -> d.isCompatibleWith("Z999")),
                "Điều hòa cho phòng Z999 (không có remote nào tương thích)");

        // Đưa 1 thiết bị đi bảo trì để kiểm tra trạng thái UNDER_MAINTENANCE
        prj2.sendToMaintenance();
        System.out.println("PRJ-02 đang: " + prj2.getStatus());
        expect(NoSuitableItemException.class,
                () -> projectorService.allocate(d -> true),
                "Máy chiếu khi PRJ-01 đã mượn, PRJ-02 bảo trì, PRJ-03 pin yếu");
        prj2.finishMaintenance();

        // ---------- TEST 10: Thiết bị được chọn chuyển sang BORROWED ----------
        printHeader("TEST 10: Chứng minh thiết bị được chọn chuyển sang BORROWED");
        System.out.println(selectedProjector.getId() + ": " + selectedProjector.getStatus());
        System.out.println(selectedAc.getId() + ": " + selectedAc.getStatus());
        System.out.println(bestMic.getId() + ": " + bestMic.getStatus());
        System.out.println("Trạng thái trong repository (cùng đối tượng): "
                + projectorRepo.findById("PRJ-01").getStatus());

        expect(DeviceNotReadyException.class,
                () -> selectedProjector.borrow(),
                "Mượn lại PRJ-01 khi đang BORROWED");
        expect(IllegalStateException.class,
                () -> prj3.returnDevice(),
                "Trả PRJ-03 khi chưa được mượn");

        projectorService.release("PRJ-01");
        System.out.println("Sau khi trả: " + selectedProjector.getId() + " -> " + selectedProjector.getStatus());

        // ---------- TEST 11: Repository generic dùng cho nhiều loại ----------
        printHeader("TEST 11: Một lớp InMemoryRepository<T> dùng cho nhiều loại");
        System.out.println("InMemoryRepository<ProjectorRemote>      : " + projectorRepo.getAll().size() + " phần tử");
        System.out.println("InMemoryRepository<AirConditionerRemote> : " + acRepo.getAll().size() + " phần tử");
        System.out.println("InMemoryRepository<WirelessMicrophone>   : " + micRepo.getAll().size() + " phần tử");

        // Dùng cho kiểu không phải thiết bị, chỉ cần có Id
        InMemoryRepository<RoomInfo> roomRepo = new InMemoryRepository<>();
        roomRepo.add(new RoomInfo("A203", "Tòa A"));
        roomRepo.add(new RoomInfo("B201", "Tòa B"));
        System.out.println("InMemoryRepository<RoomInfo>             : " + roomRepo.getAll().size()
                + " phần tử, tìm A203 -> " + roomRepo.findById("A203"));

        // getAll() không làm lộ collection nội bộ
        expect(UnsupportedOperationException.class,
                () -> projectorRepo.getAll().add(prj1),
                "Cố sửa danh sách trả về từ getAll()");

        // ---------- TEST 12 (mở rộng): Thêm loại thiết bị mới ----------
        printHeader("TEST 12: Thêm InteractiveDisplayRemote mà không sửa AllocationService");
        InMemoryRepository<InteractiveDisplayRemote> displayRepo = new InMemoryRepository<>();
        displayRepo.add(new InteractiveDisplayRemote("DSP-01", "Remote màn hình tương tác", "Kho D", false));
        displayRepo.add(new InteractiveDisplayRemote("DSP-02", "Remote màn hình tương tác 2", "Kho D", true));

        AllocationService<InteractiveDisplayRemote> displayService = new AllocationService<>(
                displayRepo,
                new FirstAvailableStrategy<>());
        System.out.println("Được chọn: " + displayService.allocate() + "  (kỳ vọng DSP-02)");

        System.out.println();
        System.out.println("Hoàn thành tất cả kịch bản kiểm thử.");
    }

    // ---------- Hàm hỗ trợ in kết quả ----------
    private static void printHeader(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    private static <T> void printList(String title, List<T> list) {
        System.out.println(title + " (" + list.size() + "):");
        for (T item : list) {
            System.out.println("  - " + item);
        }
    }

    // Chạy một hành động và kiểm tra nó có ném đúng loại exception hay không
    private static void expect(Class<? extends RuntimeException> expected, Runnable action, String description) {
        try {
            action.run();
            System.out.println("[SAI] " + description + ": không có exception nào được ném!");
        } catch (RuntimeException ex) {
            if (expected.isInstance(ex)) {
                System.out.println("[ĐÚNG] " + description + " -> " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            } else {
                System.out.println("[SAI] " + description + ": mong đợi " + expected.getSimpleName()
                        + " nhưng nhận được " + ex.getClass().getSimpleName());
            }
        }
    }
}
