package com.ms.reggie.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ms.reggie.dto.DishDto;
import com.ms.reggie.pojo.Category;
import com.ms.reggie.pojo.Dish;
import com.ms.reggie.pojo.DishFlavor;
import com.ms.reggie.service.CategoryService;
import com.ms.reggie.service.DishFlavorService;
import com.ms.reggie.service.DishService;
import com.ms.reggie.util.R;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 菜品管理(Dish)表控制层
 *
 * @author SerMs
 * @since 2022-05-07 23:32:36
 */
@RestController
@Slf4j
@RequestMapping("dish")
public class DishController {

    /**
     * 服务对象
     */
    @Resource
    private DishService dishService;

    @Resource
    private DishFlavorService dishFlavorService;

    @Resource
    private CategoryService categoryService;

    @GetMapping("/categoiryList")
    public R<List> GetCategoiry() {
        QueryWrapper<Category> categoryQueryWrapper = new QueryWrapper<>();
        List<Category> list = categoryService.list(categoryQueryWrapper.select("name", "id"));
        log.info("===={}", list);
        return R.success(list);
    }

    /**
     * 分页查询菜品列表
     *
     * @param page
     * @param pageSize
     * @param name
     * @return
     */
    @GetMapping("/page")
    public R<Page> page(int page, int pageSize, String name, long categoiryid) {
        log.info("====categoiry__id值为:::{}", categoiryid);
        //创建分页构造器对象
        Page<Dish> pageInfo = new Page<>(page, pageSize);
        Page<DishDto> dishDtoPage = new Page<>();

        //条件构造器
        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper<>();

        //添加过滤条件
        queryWrapper.like(name != null, Dish::getName, name).eq(categoiryid != 0, Dish::getCategoryId, categoiryid);


        //排序条件
        queryWrapper.orderByDesc(Dish::getUpdateTime);

        dishService.page(pageInfo, queryWrapper);
        //对象拷贝 把pageInfo数据除了records集合外全部拷贝到dishDtoPage
        BeanUtils.copyProperties(pageInfo, dishDtoPage, "records");

        //获取到数据集合
        List<Dish> records = pageInfo.getRecords();

        List<DishDto> list = records.stream().map((item) -> {
            DishDto dishDto = new DishDto();

            BeanUtils.copyProperties(item, dishDto);

            Long categoryId = item.getCategoryId(); //分类id
            //根据id查询分类
            Category category = categoryService.getById(categoryId);
            if (categoryId != null) {
                dishDto.setCategoryName(category.getName());
            }
            return dishDto;
        }).collect(Collectors.toList());
        dishDtoPage.setRecords(list);

        //执行分页查询 categoryName
        return R.success(dishDtoPage);
    }


    /**
     * 新增菜品
     *
     * @param dishDto
     * @return
     */
    @PostMapping
    @CacheEvict(value = "DishCache", allEntries = true)
    public R<String> save(@RequestBody DishDto dishDto) {
        dishService.saveWithFlavor(dishDto);
        return R.success("新增菜品成功");
    }

    /**
     * 修改菜品
     *
     * @param dishDto
     * @return
     */
    @PutMapping
    @CacheEvict(value = "DishCache", allEntries = true)
    public R<String> put(@RequestBody DishDto dishDto) {
        dishService.updateWithFlavor(dishDto);
        return R.success("更新菜品成功");
    }

    /**
     * 根据id查询菜品信息和对应的口味信息
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public R<DishDto> get(@PathVariable Long id) {
        DishDto dishDto = dishService.getByIdWithFlavor(id);
        return R.success(dishDto);
    }

    /**
     * 条件删除,多条或单条
     *
     * @param ids
     * @return
     */
    @DeleteMapping
    @CacheEvict(value = "DishCache", allEntries = true)
    public R<String> deleteDish(@RequestParam List<Long> ids) {
        dishService.deleteByIdWithFlavor(ids);
        return R.success("删除成功");
    }


    /**
     * 停售起售
     *
     * @param ids
     * @param status
     * @return
     */
    @PostMapping("/status")
    @CacheEvict(value = "DishCache", allEntries = true)
    public R<String> postDish(@RequestParam List<Long> ids, @RequestParam int status) {
        for (Long id : ids) {
            Dish byId = dishService.getById(id);
            byId.setStatus(status);
            dishService.updateById(byId);
        }
        return R.success("操作成功");
    }


    /**
     * 根据条件查询对应菜品数据
     *
     * @param dish
     * @return
     */
//    @GetMapping("/list")
//    public R<List<Dish>> list(Dish dish) {
//
//        //创建条件构造器
//        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper<>();
//        //条件
//        queryWrapper.eq(dish.getCategoryId() != null, Dish::getCategoryId, dish.getCategoryId());
//        //查询状态为1,启售的菜品
//        queryWrapper.eq(Dish::getStatus, 1);
//        //排序
//        queryWrapper.orderByAsc(Dish::getSort).orderByDesc(Dish::getUpdateTime);
//
//        List<Dish> list = dishService.list(queryWrapper);
//
//        return R.success(list);
//    }
    @GetMapping("/list")
    @Cacheable(value = "DishCache", key = "#dish.categoryId + '_' + #dish.status", condition = "#dish.getStatus() != null")
    public R<List<DishDto>> list(Dish dish) {
//        log.info("dish:{}", dish);
        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.isNotEmpty(dish.getName()), Dish::getName, dish.getName());
        queryWrapper.eq(null != dish.getCategoryId(), Dish::getCategoryId, dish.getCategoryId());
        //添加条件，查询状态为1（起售状态）的菜品
        queryWrapper.eq(Dish::getStatus, 1);
        queryWrapper.orderByDesc(Dish::getUpdateTime);

        //插入条件菜品对应的口味信息
        List<Dish> dishs = dishService.list(queryWrapper);

        List<DishDto> dishDtos = dishs.stream().map(item -> {
            DishDto dishDto = new DishDto();
            BeanUtils.copyProperties(item, dishDto);
            Category category = categoryService.getById(item.getCategoryId());
            if (category != null) {
                dishDto.setCategoryName(category.getName());
            }
            LambdaQueryWrapper<DishFlavor> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DishFlavor::getDishId, item.getId());

            dishDto.setFlavors(dishFlavorService.list(wrapper));
            return dishDto;
        }).collect(Collectors.toList());
        return R.success(dishDtos);
    }
}

